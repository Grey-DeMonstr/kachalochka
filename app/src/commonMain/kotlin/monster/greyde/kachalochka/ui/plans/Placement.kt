package monster.greyde.kachalochka.ui.plans

import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.visitMachines
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.DeviceClock
import monster.greyde.kachalochka.ui.format.planTitle
import monster.greyde.kachalochka.ui.timer.RestTimer
import monster.greyde.kachalochka.ui.visit.SetRecorder

/**
 * Puts machines into a plan or into today's visit for the account a screen offered the action
 * to. Once another account is active, a call writes nothing and returns null or false (spec §4.3).
 */
class Placement(
    private val plans: PlanRepository,
    private val visits: VisitRepository,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val currentUser: CurrentUser,
    private val clock: DeviceClock,
    private val restTimer: RestTimer,
    private val sync: SyncTrigger,
) {
    /** [owner]'s plans as "В план…" offers them, named and ordered as in the list. */
    suspend fun choices(owner: UserId?): List<PlanChoiceUi>? {
        val names = machines.all(owner).associate { it.id to it.name }
        val choices =
            plans.all(owner).map { plan ->
                PlanChoiceUi(plan.id, planTitle(plan.name, plan.machineIds.mapNotNull(names::get)))
            }
        return choices.takeIf { stillActive(owner) }
    }

    /** Appends the machines of [added] that [plan] lacks; false when the plan is gone. */
    suspend fun addToPlan(
        owner: UserId?,
        plan: PlanId,
        added: List<MachineId>,
    ): Boolean {
        val stored = plans.byId(plan)?.takeIf { it.userId == owner && !it.deleted }
        if (stored == null || !stillActive(owner)) return false
        val missing = added.distinct() - stored.machineIds.toSet()
        if (missing.isNotEmpty()) {
            plans.upsert(
                stored.copy(machineIds = stored.machineIds + missing, updatedAt = clock.now()),
            )
        }
        return true
    }

    /** As [addToPlan] with [owner]'s visit on [day]; false also when there is no visit. */
    suspend fun addVisitToPlan(
        owner: UserId?,
        plan: PlanId,
        day: CalendarDay,
    ): Boolean {
        val shown = visits.shownOn(owner, day, sets, clock::offsetAt) ?: return false
        return addToPlan(owner, plan, visitMachines(shown.sets, shown.visit.planned))
    }

    /** The machines of [owner]'s visit on [day] in the visit's order. */
    suspend fun machinesOn(
        owner: UserId?,
        day: CalendarDay,
    ): List<MachineId> {
        val shown = visits.shownOn(owner, day, sets, clock::offsetAt) ?: return emptyList()
        return visitMachines(shown.sets, shown.visit.planned)
    }

    /** Plans [planned] in today's visit; returns today, which the caller opens. */
    suspend fun planToday(
        owner: UserId?,
        planned: List<MachineId>,
    ): CalendarDay? = intoToday(owner) { it.plan(owner, planned) }

    /** Plans the machines of [owner]'s visit on [from] in today's visit, as [planToday] does. */
    suspend fun repeatToday(
        owner: UserId?,
        from: CalendarDay,
    ): CalendarDay? = intoToday(owner) { it.planFrom(owner, from) }

    /**
     * Plans [started] in today's visit and deletes it unless it is pinned. The visit is written
     * first, so a failed write keeps the plan.
     */
    suspend fun startPlan(
        owner: UserId?,
        started: Plan,
    ): CalendarDay? =
        intoToday(owner) {
            it.plan(owner, started.machineIds)
            if (!started.pinned) {
                plans.upsert(started.copy(deleted = true, updatedAt = clock.now()))
            }
        }

    private suspend fun intoToday(
        owner: UserId?,
        write: suspend (SetRecorder) -> Unit,
    ): CalendarDay? {
        if (!stillActive(owner)) return null
        val today = clock.today()
        write(SetRecorder(today, visits, machines, sets, clock, restTimer, sync))
        return today.takeIf { stillActive(owner) }
    }

    private suspend fun stillActive(owner: UserId?) = currentUser.id() == owner
}
