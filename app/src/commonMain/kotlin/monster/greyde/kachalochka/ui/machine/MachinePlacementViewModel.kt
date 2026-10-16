package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.planTitle
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.timer.RestTimer
import monster.greyde.kachalochka.ui.visit.SetRecorder
import kotlin.time.Clock

/**
 * A machine a screen shows: the account's own, or a group mate's when [ofFriend]. [group] is the
 * group read the screen holds, used instead of a new one when it was made for the active account.
 */
data class PlacedMachine(
    val id: MachineId,
    val ofFriend: Boolean = false,
    val group: GroupMachines? = null,
)

data class PlanChoiceUi(
    val id: PlanId,
    val title: String,
)

/** [machine] is the one the screen showed, so another machine's screen shows nothing. */
data class PlacementNoticeUi(
    val machine: MachineId,
    val text: String,
)

data class PlacementUi(
    /** The plans offered while "В план…" asks which; null while it does not ask. */
    val choices: List<PlanChoiceUi>? = null,
    val notice: PlacementNoticeUi? = null,
)

/**
 * Puts the machine of a machine screen into a plan or into today's visit. A friend's machine, or
 * another account's on the device, goes in as the account's machine of its cluster, else as a new
 * linked copy. Every write checks first that the account it is for is still the active one (spec
 * §4.3).
 */
class MachinePlacementViewModel(
    private val plans: PlanRepository,
    private val visits: VisitRepository,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val catalogue: MachineCatalogue,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val restTimer: RestTimer,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlacementUi())
    val state: StateFlow<PlacementUi> = mutableState
    private val writes = WriteGuard(viewModelScope)

    /** The machine the open choice is for, and the account it was offered to. */
    private var choosing: Pair<PlacedMachine, UserId?>? = null

    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                choosing = null
                mutableState.value = PlacementUi()
            }
        }
    }

    fun choosePlan(machine: PlacedMachine) {
        viewModelScope.launch {
            val owner = currentUser.id()
            val names = machines.all(owner).associate { it.id to it.name }
            val choices =
                plans.all(owner).map { plan ->
                    val live = plan.machineIds.mapNotNull(names::get)
                    PlanChoiceUi(plan.id, planTitle(plan.name, live))
                }
            if (!stillActive(owner)) return@launch
            choosing = machine to owner
            mutableState.value = PlacementUi(choices = choices)
        }
    }

    fun cancelChoice() {
        choosing = null
        mutableState.value = mutableState.value.copy(choices = null)
    }

    /** [onStoodIn] follows once another account's machine has the account's own standing in. */
    fun addTo(
        plan: PlanId,
        onStoodIn: () -> Unit = {},
    ) {
        val title =
            mutableState.value.choices
                ?.firstOrNull { it.id == plan }
                ?.title ?: return
        val (machine, owner) = choosing ?: return
        cancelChoice()
        writes.launch {
            val own = standIn(owner, machine, onStoodIn) ?: return@launch
            val stored = plans.byId(plan)?.takeIf { it.userId == owner && !it.deleted }
            if (stored == null || !stillActive(owner)) return@launch
            if (own !in stored.machineIds) {
                plans.upsert(
                    stored.copy(machineIds = stored.machineIds + own, updatedAt = clock.now()),
                )
            }
            notice(machine, AppStrings.current.addedTo(title))
        }
    }

    fun newPlan(
        onStoodIn: () -> Unit = {},
        onOpen: (MachineId) -> Unit,
    ) {
        val (machine, owner) = choosing ?: return
        cancelChoice()
        writes.launch {
            val own = standIn(owner, machine, onStoodIn) ?: return@launch
            if (stillActive(owner)) onOpen(own)
        }
    }

    /** Plans the machine in today's visit, then hands on today. */
    fun planToday(
        machine: PlacedMachine,
        onStoodIn: () -> Unit = {},
        onOpen: (CalendarDay) -> Unit,
    ) {
        writes.launch {
            val owner = currentUser.id()
            val own = standIn(owner, machine, onStoodIn) ?: return@launch
            if (!stillActive(owner)) return@launch
            val today = today()
            recorder(today).plan(owner, listOf(own))
            if (stillActive(owner)) onOpen(today)
        }
    }

    fun dismissNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    /**
     * [owner]'s machine for [machine], or null when there is none to use, the read failed or
     * another account became active meanwhile.
     */
    private suspend fun standIn(
        owner: UserId?,
        machine: PlacedMachine,
        onStoodIn: () -> Unit,
    ): MachineId? {
        if (!stillActive(owner)) return null
        if (!machine.ofFriend) return ownMachine(owner, machine, onStoodIn)
        val viewer = owner ?: return null
        val group =
            machine.group?.takeIf { it.owner == viewer }
                ?: reading { catalogue.group(viewer) }.getOrElse {
                    if (stillActive(viewer)) notice(machine, AppStrings.current.offline)
                    return null
                }
        if (!stillActive(viewer)) return null
        val own =
            reading { catalogue.friendStandIn(viewer, machine.id, group) }.getOrElse {
                notice(machine, AppStrings.current.offline)
                return null
            }
        if (own == null) {
            notice(machine, AppStrings.current.machineGone)
            return null
        }
        onStoodIn()
        return own.id
    }

    /** Another account's stored machine stands in as in [MachineCatalogue.ownCopy]. */
    private suspend fun ownMachine(
        owner: UserId?,
        machine: PlacedMachine,
        onStoodIn: () -> Unit,
    ): MachineId? {
        val shown = machines.byId(machine.id)?.takeIf { !it.deleted } ?: return null
        if (shown.userId == owner) return shown.id
        val found = catalogue.deviceStandIn(owner, shown)
        if (!stillActive(owner)) return null
        val own = found ?: catalogue.ownCopy(owner, shown)
        onStoodIn()
        return own.id
    }

    private fun recorder(day: CalendarDay) =
        SetRecorder(day, visits, machines, sets, clock, utcOffset, restTimer, sync)

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }

    private suspend fun stillActive(owner: UserId?) = currentUser.id() == owner

    private fun notice(
        machine: PlacedMachine,
        text: String,
    ) {
        mutableState.value = mutableState.value.copy(notice = PlacementNoticeUi(machine.id, text))
    }
}
