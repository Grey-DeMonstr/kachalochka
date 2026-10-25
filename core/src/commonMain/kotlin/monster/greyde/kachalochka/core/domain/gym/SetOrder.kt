package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

fun nextPosition(visitSets: List<WorkoutSet>): Int =
    (visitSets.maxOfOrNull { it.position } ?: 0) + 1

/** The sets whose position changes when [machine]'s block moves to [index] among the machines. */
fun machineMovedTo(
    visitSets: List<WorkoutSet>,
    machine: MachineId,
    index: Int,
    now: Instant,
): List<WorkoutSet> {
    val order = groupByMachine(visitSets).map { it.machineId }.toMutableList()
    val from = order.indexOf(machine)
    if (from < 0 || index !in order.indices || index == from) return emptyList()
    order.add(index, order.removeAt(from))
    return changed(visitSets, renumbered(visitSets, order), now)
}

private fun renumbered(
    visitSets: List<WorkoutSet>,
    machineOrder: List<MachineId>,
): List<WorkoutSet> {
    val byMachine = groupByMachine(visitSets).associate { it.machineId to it.sets }
    return machineOrder
        .flatMap { byMachine.getValue(it) }
        .mapIndexed { index, set -> set.copy(position = index + 1) }
}

private fun changed(
    before: List<WorkoutSet>,
    after: List<WorkoutSet>,
    now: Instant,
): List<WorkoutSet> {
    val was = before.associate { it.id to it.position }
    return after.filter { was[it.id] != it.position }.map { it.copy(updatedAt = now) }
}
