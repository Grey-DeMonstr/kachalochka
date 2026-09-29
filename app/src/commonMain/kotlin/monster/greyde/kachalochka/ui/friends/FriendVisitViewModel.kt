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
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.dayAt
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.keptVisit
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.setsSummary
import monster.greyde.kachalochka.ui.machine.visibleLinks
import kotlin.time.Clock

data class FriendVisitUiState(
    val title: String,
    val setCountLabel: String,
    val groups: List<FriendSetGroupUi>,
)

data class FriendSetGroupUi(
    val machineId: MachineId,
    val title: String,
    val summary: String,
    val sets: List<FriendSetRowUi>,
)

data class FriendSetRowUi(
    val id: WorkoutSetId,
    val title: String,
    val value: String,
)

class FriendVisitViewModel(
    private val member: UserId,
    private val name: String,
    private val day: CalendarDay,
    private val friends: FriendsRepository,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val machineLinks: MachineLinkRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FriendVisitUiState?>(null)
    val state: StateFlow<FriendVisitUiState?> = mutableState
    private val mutableOffline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = mutableOffline

    private var loading: Job? = null

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

    /** The sets of the day's visit [keptVisit] would show: the newest with live sets, else all. */
    private suspend fun setsOn(day: CalendarDay): List<WorkoutSet> {
        val sameDay = friends.visits(member).filter { it.dayAt(utcOffset::at) == day }
        if (sameDay.isEmpty()) return emptyList()
        val setsByVisit =
            if (sameDay.size > 1) sameDay.associate { it.id to friends.sets(it) } else emptyMap()
        val visit = keptVisit(sameDay) { setsByVisit[it]?.isNotEmpty() == true }
        return setsByVisit[visit.id] ?: friends.sets(visit)
    }

    private suspend fun load() {
        reading {
            val visitSets = setsOn(day)
            val theirs = friends.machines(member)
            val me = currentUser.id()
            val links = me?.let { visibleLinks(it, friends, machineLinks) }.orEmpty()
            val names = namesForViewer(theirs, machines.all(me), MachineClusters(links))
            stateOf(visitSets, theirs.associateBy { it.id }, names, profiles.preferredUnit(me))
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
    ): FriendVisitUiState {
        val now = clock.now()
        val today = CalendarDay.of(now, utcOffset.at(now))
        return FriendVisitUiState(
            title = "$name · ${dayMonthLabel(day, today.year)}",
            setCountLabel = setCount(visitSets.size),
            groups =
                groupByMachine(visitSets).map {
                    groupUi(it.machineId, it.sets, names, machinesById, preferred)
                },
        )
    }

    private fun groupUi(
        machineId: MachineId,
        machineSets: List<WorkoutSet>,
        names: Map<MachineId, String>,
        machinesById: Map<MachineId, Machine>,
        preferred: PreferredWeightUnit,
    ): FriendSetGroupUi {
        val title = names[machineId].orEmpty()
        val machine = machinesById[machineId]
        return FriendSetGroupUi(
            machineId = machineId,
            title = title,
            summary = machine?.let { setsSummary(it, machineSets, preferred) }.orEmpty(),
            sets =
                machineSets.mapIndexed { index, set ->
                    FriendSetRowUi(
                        set.id,
                        "#${index + 1}",
                        machine?.let { setValue(set.weight, set.reps, it, preferred) }
                            ?: setValue(set.weight, set.reps, "кг"),
                    )
                },
        )
    }
}
