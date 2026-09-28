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

/** The sets whose position changes when [set] moves to [index] among its machine's sets. */
fun setMovedTo(
    visitSets: List<WorkoutSet>,
    set: WorkoutSetId,
    index: Int,
    now: Instant,
): List<WorkoutSet> {
    val groups = groupByMachine(visitSets)
    val group = groups.firstOrNull { g -> g.sets.any { it.id == set } } ?: return emptyList()
    val from = group.sets.indexOfFirst { it.id == set }
    if (index !in group.sets.indices || index == from) return emptyList()
    val moved = group.sets.toMutableList().apply { add(index, removeAt(from)) }
    val renumbered =
        groups
            .flatMap { if (it.machineId == group.machineId) moved else it.sets }
            .mapIndexed { i, s -> s.copy(position = i + 1) }
    return changed(visitSets, renumbered, now)
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
