package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
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

/** A machine a screen shows: the account's own, or a group mate's when [ofFriend]. */
data class PlacedMachine(
    val id: MachineId,
    val ofFriend: Boolean = false,
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
 * Puts the machine of a machine screen into a plan or into today's visit. A friend's machine
 * goes in as the account's machine of its cluster, else as a new linked copy.
 */
class MachinePlacementViewModel(
    private val plans: PlanRepository,
    private val visits: VisitRepository,
    private val machines: MachineRepository,
    private val links: MachineLinkRepository,
    private val sets: WorkoutSetRepository,
    private val currentUser: CurrentUser,
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

    fun choosePlan(machine: PlacedMachine) {
        viewModelScope.launch {
            val owner = currentUser.id()
            val names = machines.all(owner).associate { it.id to it.name }
            val choices =
                plans.all(owner).map { plan ->
                    val live = plan.machineIds.mapNotNull(names::get)
                    PlanChoiceUi(plan.id, planTitle(plan.name, live))
                }
            choosing = machine to owner
            mutableState.value = PlacementUi(choices = choices)
        }
    }

    fun cancelChoice() {
        choosing = null
        mutableState.value = mutableState.value.copy(choices = null)
    }

    /** [onStoodIn] follows once a friend's machine has the account's machine standing in. */
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
            if (currentUser.id() != owner) return@launch
            val own = standIn(owner, machine, onStoodIn) ?: return@launch
            val stored = plans.byId(plan)?.takeIf { it.userId == owner && !it.deleted }
            if (stored == null) return@launch
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
            if (currentUser.id() != owner) return@launch
            standIn(owner, machine, onStoodIn)?.let(onOpen)
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
            val now = clock.now()
            val today = CalendarDay.of(now, utcOffset.at(now))
            SetRecorder(today, visits, machines, links, sets, clock, utcOffset, restTimer, sync)
                .plan(owner, listOf(own))
            onOpen(today)
        }
    }

    fun dismissNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    /** [owner]'s machine for [machine]; null when there is none to use or the read failed. */
    private suspend fun standIn(
        owner: UserId?,
        machine: PlacedMachine,
        onStoodIn: () -> Unit,
    ): MachineId? {
        if (!machine.ofFriend) {
            return machines.byId(machine.id)?.takeIf { it.userId == owner && !it.deleted }?.id
        }
        val viewer = owner ?: return null
        return reading { catalogue.standIn(viewer, machine.id, catalogue.group(viewer)) }
            .onFailure { notice(machine, AppStrings.current.offline) }
            .getOrNull()
            ?.also { onStoodIn() }
            ?.id
    }

    private fun notice(
        machine: PlacedMachine,
        text: String,
    ) {
        mutableState.value = mutableState.value.copy(notice = PlacementNoticeUi(machine.id, text))
    }
}
