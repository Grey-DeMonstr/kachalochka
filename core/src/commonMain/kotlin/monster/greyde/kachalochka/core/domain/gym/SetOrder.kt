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
    val blocks = groupByMachine(visitSets).toMutableList()
    val from = blocks.indexOfFirst { it.machineId == machine }
    if (from < 0 || index !in blocks.indices || index == from) return emptyList()
    blocks.add(index, blocks.removeAt(from))
    return changed(visitSets, renumbered(blocks.map { it.sets }), now)
}

/** The sets whose position changes when [set] moves to [index] among its machine's sets. */
fun setMovedTo(
    visitSets: List<WorkoutSet>,
    set: WorkoutSetId,
    index: Int,
    now: Instant,
): List<WorkoutSet> {
    val blocks = groupByMachine(visitSets).map { it.sets.toMutableList() }
    val block = blocks.firstOrNull { sets -> sets.any { it.id == set } } ?: return emptyList()
    val from = block.indexOfFirst { it.id == set }
    if (index !in block.indices || index == from) return emptyList()
    block.add(index, block.removeAt(from))
    return changed(visitSets, renumbered(blocks), now)
}

private fun renumbered(blocks: List<List<WorkoutSet>>): List<WorkoutSet> =
    blocks.flatten().mapIndexed { index, set -> set.copy(position = index + 1) }

private fun changed(
    before: List<WorkoutSet>,
    after: List<WorkoutSet>,
    now: Instant,
): List<WorkoutSet> {
    val was = before.associate { it.id to it.position }
    return after.filter { was[it.id] != it.position }.map { it.copy(updatedAt = now) }
}
