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
        visit: VisitId = VisitId.random(),
    ) = WorkoutSet(
        WorkoutSetId.random(),
        null,
        visit,
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
            listOf(MachinePeaks(press, SetPeak(80.0, 8), SetPeak(60.0, 15), t0 + 5.minutes, 5)),
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
    fun visits_count_the_distinct_visits_with_a_live_set_on_the_machine() {
        val monday = VisitId.random()
        val peaks =
            machinePeaks(
                listOf(
                    set(press, 70.0, 10, 1, visit = monday),
                    set(press, 70.0, 10, 2, visit = monday),
                    set(press, 70.0, 10, 3),
                    set(press, 70.0, 10, 4, deleted = true),
                ),
            )

        assertEquals(2, peaks.single().visits)
    }

    @Test
    fun each_machine_peaks_on_its_own() {
        val peaks = machinePeaks(listOf(set(press, 70.0, 10, 1), set(row, 40.0, 12, 2)))

        assertEquals(setOf(press, row), peaks.map { it.machineId }.toSet())
    }

    @Test
    fun the_record_is_the_heaviest_except_on_a_gravitron() {
        val peaks = MachinePeaks(press, SetPeak(30.0, 6), SetPeak(20.0, 10), t0, 1)

        assertEquals(SetPeak(30.0, 6), peaks.best(WeightMode.Total))
        assertEquals(SetPeak(30.0, 6), peaks.best(WeightMode.PerSide))
        assertEquals(SetPeak(20.0, 10), peaks.best(WeightMode.Counterweight))
    }
}
