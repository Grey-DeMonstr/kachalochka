package monster.greyde.kachalochka.core.domain.gym

data class MachineSets(
    val machineId: MachineId,
    val sets: List<WorkoutSet>,
)

fun groupByMachine(sets: List<WorkoutSet>): List<MachineSets> =
    sets
        .sortedBy { it.recordedAt }
        .groupBy { it.machineId }
        .map { (machineId, machineSets) -> MachineSets(machineId, machineSets) }

data class VisitSummary(
    val machineCount: Int,
    val setCount: Int,
    val lastSet: WorkoutSet?,
)

fun summarize(sets: List<WorkoutSet>): VisitSummary {
    val live = sets.filterNot { it.deleted }
    return VisitSummary(
        machineCount = live.map { it.machineId }.distinct().size,
        setCount = live.size,
        lastSet = live.maxByOrNull { it.recordedAt },
    )
}
