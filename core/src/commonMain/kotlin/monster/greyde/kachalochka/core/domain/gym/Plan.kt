package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.identity.newUuidV4
import monster.greyde.kachalochka.core.domain.identity.requireUuidV4
import kotlin.jvm.JvmInline
import kotlin.time.Instant

const val PLAN_NAME_LENGTH = 40

@JvmInline
value class PlanId(
    val value: String,
) {
    init {
        requireUuidV4(value, "PlanId")
    }

    companion object {
        fun random(): PlanId = PlanId(newUuidV4())
    }
}

/** Machines to do on a visit to come; [machineIds] is in plan order. */
data class Plan(
    val id: PlanId,
    val userId: UserId?,
    val name: String,
    val machineIds: List<MachineId>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deleted: Boolean,
)

val planOrder: Comparator<Plan> = compareBy<Plan>({ it.createdAt }, { it.id.value })

/**
 * The visit's planned list once [plan] starts on it: [planned] followed by the plan's [live]
 * machines that are neither [recorded] in the visit nor planned already.
 */
fun startedPlanned(
    planned: List<MachineId>,
    recorded: Collection<MachineId>,
    plan: List<MachineId>,
    live: Set<MachineId>,
): List<MachineId> =
    planned + plan.distinct().filter { it in live && it !in recorded && it !in planned }

/** The planned machines the visit shows as planned rows, in planned order. */
fun plannedWithoutSets(
    planned: List<MachineId>,
    sets: List<WorkoutSet>,
    live: Set<MachineId>,
): List<MachineId> {
    val recorded = sets.map { it.machineId }.toSet()
    return planned.distinct().filter { it in live && it !in recorded }
}

/** A visit's machines in the order it lists them: by their first set, then the planned ones. */
fun visitMachines(
    sets: List<WorkoutSet>,
    planned: List<MachineId>,
): List<MachineId> = (sets.map { it.machineId } + planned).distinct()
