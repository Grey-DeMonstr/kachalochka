package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

/** A weight and the most reps any set lifted at it. */
data class SetPeak(
    val weight: Double,
    val reps: Int,
)

/** How far a machine's live sets reach, and when it was last used. */
data class MachinePeaks(
    val machineId: MachineId,
    val heaviest: SetPeak,
    val lightest: SetPeak,
    val lastAt: Instant,
) {
    /** The machine's record: its heaviest set, or on a gravitron, where less is more, lightest. */
    fun best(mode: WeightMode): SetPeak =
        when (mode) {
            WeightMode.Counterweight -> lightest
            else -> heaviest
        }
}

fun machinePeaks(sets: List<WorkoutSet>): List<MachinePeaks> =
    sets
        .filterNot { it.deleted }
        .groupBy { it.machineId }
        .map { (machine, onMachine) ->
            MachinePeaks(
                machine,
                peakAt(onMachine, onMachine.maxOf { it.weight }),
                peakAt(onMachine, onMachine.minOf { it.weight }),
                onMachine.maxOf { it.recordedAt },
            )
        }

private fun peakAt(
    sets: List<WorkoutSet>,
    weight: Double,
): SetPeak = SetPeak(weight, sets.filter { it.weight == weight }.maxOf { it.reps })
