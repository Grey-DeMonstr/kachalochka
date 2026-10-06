package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.machineOrder
import monster.greyde.kachalochka.core.domain.gym.rankMachines
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.UnsavedChoices
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

data class TagChoiceUi(
    val name: String,
    val chosen: Boolean,
)

data class PickerUiState(
    val query: String = "",
    val createLabel: String? = null,
    /** What a created machine starts with, under [createLabel]. */
    val createHint: String = "",
    val sectionLabel: String = "",
    val tags: List<TagChoiceUi> = emptyList(),
    val rows: List<MachineCardUi> = emptyList(),
    val friendSections: List<FriendSectionUi> = emptyList(),
    /** Own machines the visit already holds, listed last. */
    val inVisitRows: List<MachineCardUi> = emptyList(),
    /** True when a search or a tag leaves nothing to list. */
    val nothingFound: Boolean = false,
    val sort: MachineSort = MachineSort.Recent,
) {
    val friendRows: List<MachineCardUi> get() = friendSections.flatMap { it.cards }
}

/** A null [day] picks for a plan. */
class MachinePickerViewModel(
    private val day: CalendarDay?,
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
    private val profiles: ProfileRepository,
    private val catalogue: MachineCatalogue,
    private val friends: FriendsRepository,
    private val friendColors: FriendColorStore,
    unsaved: UnsavedChoices,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var own: OwnMachines? = null
    private var group: GroupMachines? = null
    private var ownPeaks: List<MachinePeaks> = emptyList()
    private var friendPeaks: List<MachinePeaks> = emptyList()
    private var colors: Map<UserId, Int> = emptyMap()
    private var preferred = PreferredWeightUnit.Kg
    private var chosenTags: Set<String> = emptySet()
    private var inVisit: Set<MachineId> = emptySet()
    private var loading: Job? = null
    private var loadingFriends: Job? = null
    private val sorting =
        machineSortChoice(profiles, currentUser, clock, sync, unsaved, viewModelScope)

    private val shown: ShownMachines? get() = own?.let { ShownMachines(it, group) }

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    /** Own machines show at once; friends' follow from the network, if it answers. */
    fun load() {
        loading?.cancel()
        loadingFriends?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                own = catalogue.own(owner)
                ownPeaks = sets.peaks(owner)
                inVisit = day?.let { machinesInVisit(owner, it) }.orEmpty()
                preferred = profiles.preferredUnit(owner)
                sorting.read(owner)
                publish()
                owner?.let(::loadFriends)
            }
    }

    private fun loadFriends(owner: UserId) {
        loadingFriends =
            viewModelScope.launch {
                group = reading { catalogue.group(owner) }.getOrNull()
                publish()
                friendPeaks = reading { friends.groupPeaks(owner) }.getOrDefault(friendPeaks)
                val offered = group?.offered(own?.machines.orEmpty()).orEmpty()
                colors =
                    reading {
                        friendColors.colorsFor(owner, offered.map { it.owner.userId }.distinct())
                    }.getOrDefault(colors)
                publish()
            }
    }

    private suspend fun machinesInVisit(
        owner: UserId?,
        day: CalendarDay,
    ): Set<MachineId> {
        val shown = visits.shownOn(owner, day, sets, utcOffset::at) ?: return emptySet()
        return shown.sets.map { it.machineId }.toSet() + shown.visit.planned
    }

    fun onQueryChange(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
        publish()
    }

    fun toggleTag(tag: String) {
        chosenTags = if (tag in chosenTags) chosenTags - tag else chosenTags + tag
        publish()
    }

    fun chooseSort(sort: MachineSort) {
        sorting.choose(sort)
        publish()
    }

    /** The tags a machine created now starts with. */
    val createTags: List<String> get() = chosenTags.sortedBy { it.lowercase() }

    /** A friend's machine becomes the account's own, linked to theirs, before it is picked. */
    fun pickFriend(
        id: MachineId,
        onPicked: (MachineId) -> Unit,
    ) {
        val offered = shown ?: return
        val friend = offered.offered.firstOrNull { it.machine.id == id } ?: return
        val offeredTo = offered.own.owner
        writes.launch {
            val owner = currentUser.id() ?: return@launch
            if (owner != offeredTo) return@launch
            onPicked(catalogue.take(owner, friend.machine).id)
        }
    }

    private fun publish() {
        val query = mutableState.value.query
        val shown = shown
        val ownMachines = shown?.own?.machines.orEmpty()
        val offered = shown?.offered.orEmpty()
        val sort = sorting.current
        val ownOrder = machineOrder(sort, ownPeaks.associateBy { it.machineId })
        val ranking = rankMachines(query, ownMachines, ownOrder, chosenTags)
        val needle = query.trim()
        val matchingOffered =
            offered.filter {
                it.machine.name.contains(needle, ignoreCase = true) &&
                    it.machine.tags.containsAll(chosenTags)
            }
        val now = clock.now()
        val offset = utcOffset.at(now)
        val cards =
            shown?.let {
                MachineCards(
                    it,
                    ownPeaks,
                    friendPeaks,
                    preferred,
                    CalendarDay.of(now, offset),
                    offset,
                )
            }
        val (alreadyIn, notIn) = ranking.machines.partition { it.id in inVisit }
        val rows = cards?.let { c -> notIn.map(c::own) }.orEmpty()
        val inVisitRows =
            cards?.let { c -> alreadyIn.map { c.own(it).copy(inVisit = true) } }.orEmpty()
        val friendOrder = machineOrder(sort, friendPeaks.associateBy { it.machineId })
        val sections = cards?.friendSections(matchingOffered, colors, friendOrder).orEmpty()
        val strings = AppStrings.current
        mutableState.value =
            PickerUiState(
                query = query,
                createLabel = strings.createNamed(needle).takeIf { ranking.offerCreate },
                createHint =
                    if (chosenTags.isEmpty()) {
                        strings.photoNoteAndSetup
                    } else {
                        strings.withTags(createTags)
                    },
                sectionLabel = if (query.isBlank()) strings.myMachines else strings.similar,
                tags =
                    (ownMachines + offered.map { it.machine })
                        .flatMap { it.tags }
                        .plus(chosenTags)
                        .distinct()
                        .sortedBy { it.lowercase() }
                        .map { TagChoiceUi(it, it in chosenTags) },
                rows = rows,
                friendSections = sections,
                inVisitRows = inVisitRows,
                nothingFound =
                    (needle.isNotEmpty() || chosenTags.isNotEmpty()) &&
                        rows.isEmpty() &&
                        sections.isEmpty() &&
                        inVisitRows.isEmpty(),
                sort = sort,
            )
    }
}
