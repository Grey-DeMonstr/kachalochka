package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.sortedTags
import monster.greyde.kachalochka.core.domain.gym.tagKey
import monster.greyde.kachalochka.core.domain.gym.toggledTag
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.UnsavedChoices
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

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
    private val reads: MachineReads,
    unsaved: UnsavedChoices,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var read: MachineRead? = null
    private var preferred = PreferredWeightUnit.Kg
    private var chosenTags: Set<String> = emptySet()
    private var inVisit: Set<MachineId> = emptySet()
    private var loading: Job? = null
    private val sorting =
        machineSortChoice(profiles, currentUser, clock, sync, unsaved, viewModelScope)

    private val shown: ShownMachines? get() = read?.shown

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    /** Own machines show at once; friends' follow from the network, if it answers. */
    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                inVisit = day?.let { machinesInVisit(owner, it) }.orEmpty()
                preferred = profiles.preferredUnit(owner)
                sorting.read(owner)
                publish()
                reads.machines(owner, withCards = true).collect {
                    read = it
                    publish()
                }
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
        chosenTags = toggledTag(chosenTags, tagKey(tag))
        publish()
    }

    fun chooseSort(sort: MachineSort) {
        sorting.choose(sort)
        publish()
    }

    /** The tags a machine created now starts with. */
    val createTags: List<String> get() = sortedTags(chosenTags)

    /** A friend's machine becomes the account's own, linked to theirs, before it is picked. */
    fun pickFriend(
        id: MachineId,
        onPicked: (MachineId) -> Unit,
    ) {
        val offered = shown ?: return
        val group = offered.group ?: return
        val friend = offered.offered.firstOrNull { it.machine.id == id } ?: return
        val offeredTo = offered.own.owner
        writes.launch {
            val owner = currentUser.id() ?: return@launch
            if (owner != offeredTo) return@launch
            onPicked(catalogue.take(owner, friend.machine, group).id)
        }
    }

    private fun publish() {
        val query = mutableState.value.query
        val sort = sorting.current
        val strings = AppStrings.current
        val read = read
        if (read == null) {
            mutableState.value = PickerUiState(query = query, sort = sort)
            return
        }
        val now = clock.now()
        val offset = utcOffset.at(now)
        val listing =
            read.listing(
                MachineQuery(query, chosenTags, inVisit),
                sort,
                preferred,
                CalendarDay.of(now, offset),
                offset,
            )
        chosenTags = listing.query.tags
        mutableState.value =
            PickerUiState(
                query = query,
                createLabel = strings.createNamed(query.trim()).takeIf { listing.offerCreate },
                createHint =
                    if (chosenTags.isEmpty()) {
                        strings.photoNoteAndSetup
                    } else {
                        strings.withTags(createTags)
                    },
                sectionLabel = if (query.isBlank()) strings.myMachines else strings.similar,
                tags = listing.tags,
                rows = listing.own,
                friendSections = listing.friendSections,
                inVisitRows = listing.inVisit,
                nothingFound = listing.nothingFound,
                sort = sort,
            )
    }
}
