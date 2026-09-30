package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MachinePeaksTest {
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val press = MachineId.random()
    private val row = MachineId.random()

    private fun set(
        machine: MachineId,
        weight: Double,
        reps: Int,
        minute: Int,
        deleted: Boolean = false,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        null,
        VisitId.random(),
        machine,
        weight,
        reps,
        0,
        t0 + minute.minutes,
        t0,
        deleted,
    )

    @Test
    fun a_machine_peaks_at_its_heaviest_and_lightest_weights_with_their_most_reps() {
        val peaks =
            machinePeaks(
                listOf(
                    set(press, 70.0, 10, 1),
                    set(press, 80.0, 6, 2),
                    set(press, 80.0, 8, 3),
                    set(press, 60.0, 12, 4),
                    set(press, 60.0, 15, 5),
                ),
            )

        assertEquals(
            listOf(MachinePeaks(press, SetPeak(80.0, 8), SetPeak(60.0, 15), t0 + 5.minutes)),
            peaks,
        )
    }

    @Test
    fun deleted_sets_reach_no_peak() {
        val peaks =
            machinePeaks(
                listOf(set(press, 70.0, 10, 1), set(press, 90.0, 10, 2, deleted = true)),
            )

        assertEquals(SetPeak(70.0, 10), peaks.single().heaviest)
        assertEquals(t0 + 1.minutes, peaks.single().lastAt)
    }

    @Test
    fun each_machine_peaks_on_its_own() {
        val peaks = machinePeaks(listOf(set(press, 70.0, 10, 1), set(row, 40.0, 12, 2)))

        assertEquals(setOf(press, row), peaks.map { it.machineId }.toSet())
    }

    @Test
    fun the_record_is_the_heaviest_except_on_a_gravitron() {
        val peaks = MachinePeaks(press, SetPeak(30.0, 6), SetPeak(20.0, 10), t0)

        assertEquals(SetPeak(30.0, 6), peaks.best(WeightMode.Total))
        assertEquals(SetPeak(30.0, 6), peaks.best(WeightMode.PerSide))
        assertEquals(SetPeak(20.0, 10), peaks.best(WeightMode.Counterweight))
    }
}
