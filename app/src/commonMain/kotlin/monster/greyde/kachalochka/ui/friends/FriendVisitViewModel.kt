package monster.greyde.kachalochka.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.namesForViewer
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.dayAt
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.shownVisit
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.machineCount
import monster.greyde.kachalochka.ui.format.setsSummaryParts
import monster.greyde.kachalochka.ui.format.weekdayDate
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.ShownMachines
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

/** [title] is the friend's name, [day] heads the list over [countLabel], its machine count. */
data class FriendVisitUiState(
    val title: String,
    val avatar: Avatar,
    val day: String,
    val countLabel: String,
    val groups: List<FriendMachineRowUi>,
)

/** A machine of the friend's visit, drawn as a row of the viewer's own visit. */
data class FriendMachineRowUi(
    val machineId: MachineId,
    val title: String,
    val note: String,
    val tags: List<String>,
    val summary: List<String>,
    val photo: Photo?,
)

class FriendVisitViewModel(
    private val member: UserId,
    private val name: String,
    private val day: CalendarDay,
    private val friends: FriendsRepository,
    private val catalogue: MachineCatalogue,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FriendVisitUiState?>(null)
    val state: StateFlow<FriendVisitUiState?> = mutableState
    private val mutableOffline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = mutableOffline

    private var loading: Job? = null

    private var spokenIn = AppStrings.current

    /** Reads again when the language changed while another screen was open. */
    fun speak() {
        if (AppStrings.current == spokenIn) return
        spokenIn = AppStrings.current
        refresh()
    }

    init {
        viewModelScope.launch {
            accounts.activeId.collect { refresh() }
        }
    }

    /** Cancels the load in flight, so one for a previous account never lands last. */
    fun refresh() {
        loading?.cancel()
        loading = viewModelScope.launch { load() }
    }

    private suspend fun setsOn(day: CalendarDay): List<WorkoutSet> {
        val sameDay = friends.visits(member).filter { it.dayAt(utcOffset::at) == day }
        return shownVisit(sameDay, friends::sets)?.sets.orEmpty()
    }

    private suspend fun load() {
        reading {
            val visitSets = setsOn(day)
            val theirs = friends.machines(member)
            val me = currentUser.id()
            val own = catalogue.own(me)
            val group = me?.let { catalogue.group(it) }
            val shown = ShownMachines(own, group)
            val names = namesForViewer(theirs, own.machines, shown.clusters)
            val avatar =
                me
                    ?.let { viewer -> friends.mates(viewer).firstOrNull { it.userId == member } }
                    ?.avatar ?: Avatar()
            stateOf(
                visitSets,
                theirs.associateBy { it.id },
                names,
                profiles.preferredUnit(me),
                avatar,
                shown,
            )
        }.onSuccess {
            mutableState.value = it
            mutableOffline.value = false
        }.onFailure {
            mutableOffline.value = true
        }
    }

    private fun stateOf(
        visitSets: List<WorkoutSet>,
        machinesById: Map<MachineId, Machine>,
        names: Map<MachineId, String>,
        preferred: PreferredWeightUnit,
        avatar: Avatar,
        shown: ShownMachines,
    ): FriendVisitUiState {
        val now = clock.now()
        val today = CalendarDay.of(now, utcOffset.at(now))
        val groups = groupByMachine(visitSets)
        return FriendVisitUiState(
            title = name,
            avatar = avatar,
            day = weekdayDate(day, withYear = day.year != today.year),
            countLabel = machineCount(groups.size),
            groups =
                groups.map {
                    groupUi(it.machineId, it.sets, names, machinesById, preferred, shown)
                },
        )
    }

    private fun groupUi(
        machineId: MachineId,
        machineSets: List<WorkoutSet>,
        names: Map<MachineId, String>,
        machinesById: Map<MachineId, Machine>,
        preferred: PreferredWeightUnit,
        shown: ShownMachines,
    ): FriendMachineRowUi {
        val machine = machinesById[machineId]
        return FriendMachineRowUi(
            machineId = machineId,
            title = names[machineId].orEmpty(),
            note = machine?.setupNote.orEmpty(),
            tags = machine?.tags.orEmpty().sortedBy { it.lowercase() },
            summary = machine?.let { setsSummaryParts(it, machineSets, preferred) }.orEmpty(),
            photo = shown.cover(machineId),
        )
    }
}
