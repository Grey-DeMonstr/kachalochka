package monster.greyde.kachalochka.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.namesForViewer
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.keptVisit
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.groupSummary
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.unitLabel
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
) : ViewModel() {
    private val mutableState = MutableStateFlow<FriendVisitUiState?>(null)
    val state: StateFlow<FriendVisitUiState?> = mutableState
    private val mutableOffline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = mutableOffline

    init {
        viewModelScope.launch {
            accounts.activeId.collect { load() }
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** Of the day's visits, the one [keptVisit] would show: the newest with live sets, else all. */
    private suspend fun visitOn(day: CalendarDay): Visit? {
        val sameDay = friends.visits(member).filter { it.day == day }
        if (sameDay.isEmpty()) return null
        val withSets =
            if (sameDay.size > 1) {
                sameDay.filter { friends.sets(it).isNotEmpty() }.map { it.id }.toSet()
            } else {
                emptySet()
            }
        return keptVisit(sameDay) { it in withSets }
    }

    private suspend fun load() {
        reading {
            val visit = visitOn(day)
            val visitSets = visit?.let { friends.sets(it) }.orEmpty()
            val theirs = friends.machines(member)
            val names = namesForViewer(theirs, machines.all(currentUser.id()))
            stateOf(visitSets, theirs.associateBy { it.id }, names)
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
    ): FriendVisitUiState {
        val now = clock.now()
        val today = CalendarDay.of(now, utcOffset.at(now))
        return FriendVisitUiState(
            title = "$name · ${dayMonthLabel(day, today.year)}",
            setCountLabel = setCount(visitSets.size),
            groups =
                groupByMachine(visitSets).map {
                    groupUi(it.machineId, it.sets, names, machinesById)
                },
        )
    }

    private fun groupUi(
        machineId: MachineId,
        machineSets: List<WorkoutSet>,
        names: Map<MachineId, String>,
        machinesById: Map<MachineId, Machine>,
    ): FriendSetGroupUi {
        val title = names[machineId].orEmpty()
        val unit = machinesById[machineId]?.let(::unitLabel) ?: "кг"
        return FriendSetGroupUi(
            machineId = machineId,
            title = title,
            summary = groupSummary(machineSets, unit),
            sets =
                machineSets.mapIndexed { index, set ->
                    FriendSetRowUi(
                        set.id,
                        "$title · подход ${index + 1}",
                        setValue(set.weight, set.reps, unit),
                    )
                },
        )
    }
}
