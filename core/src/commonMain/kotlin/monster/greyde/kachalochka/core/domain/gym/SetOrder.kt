package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

fun nextPosition(visitSets: List<WorkoutSet>): Int =
    (visitSets.maxOfOrNull { it.position } ?: 0) + 1

/** The sets whose position changes when [machine] trades places with its neighbour. */
fun machineMoved(
    visitSets: List<WorkoutSet>,
    machine: MachineId,
    direction: Int,
    now: Instant,
): List<WorkoutSet> {
    val order = groupByMachine(visitSets).map { it.machineId }.toMutableList()
    val from = order.indexOf(machine)
    val to = from + direction
    if (from < 0 || to !in order.indices) return emptyList()
    order.add(to, order.removeAt(from))
    return changed(visitSets, renumbered(visitSets, order), now)
}

/** The sets whose position changes when [set] trades places with its neighbour on its machine. */
fun setMoved(
    visitSets: List<WorkoutSet>,
    set: WorkoutSetId,
    direction: Int,
    now: Instant,
): List<WorkoutSet> {
    val groups = groupByMachine(visitSets)
    val onMachine =
        groups.firstOrNull { g -> g.sets.any { it.id == set } }?.sets ?: return emptyList()
    val from = onMachine.indexOfFirst { it.id == set }
    val to = from + direction
    if (to !in onMachine.indices) return emptyList()
    // A swap keeps every other set in place only when no two sets share a position; old rows
    // and a reorder written halfway get distinct numbers first.
    val numbered =
        if (visitSets.distinctBy { it.position }.size < visitSets.size) {
            renumbered(visitSets, groups.map { it.machineId })
        } else {
            visitSets
        }
    val a = numbered.first { it.id == onMachine[from].id }
    val b = numbered.first { it.id == onMachine[to].id }
    val swapped =
        numbered.map {
            when (it.id) {
                a.id -> it.copy(position = b.position)
                b.id -> it.copy(position = a.position)
                else -> it
            }
        }
    return changed(visitSets, swapped, now)
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
