package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StatisticsTest {
    private val day = 86_400L

    @Test
    fun a_period_runs_back_from_today_by_its_months() {
        val today = CalendarDay(2026, 9, 29)

        assertEquals(CalendarDay(2026, 8, 29), StatsPeriod.Month.start(today))
        assertEquals(CalendarDay(2026, 6, 29), StatsPeriod.ThreeMonths.start(today))
        assertEquals(CalendarDay(2026, 3, 29), StatsPeriod.SixMonths.start(today))
        assertEquals(CalendarDay(2025, 9, 29), StatsPeriod.Year.start(today))
        assertEquals(CalendarDay(2026, 9, 1), StatsPeriod.Month.start(CalendarDay(2026, 10, 1)))
    }

    @Test
    fun the_worst_set_is_the_lightest_with_the_fewest_reps_and_the_heaviest_on_a_gravitron() {
        val sets =
            listOf(
                set(VISIT_A, 60.0, 10, 0),
                set(VISIT_A, 50.0, 12, 1),
                set(VISIT_A, 50.0, 8, 2),
                set(VISIT_A, 70.0, 5, 3),
            )

        assertEquals(sets[2], worstSet(sets, WeightMode.Total))
        assertEquals(sets[3], worstSet(sets, WeightMode.Counterweight))
        assertEquals(sets[3], bestSet(sets, WeightMode.Total))
        assertEquals(sets[1], bestSet(sets, WeightMode.Counterweight))
    }

    @Test
    fun with_sets_before_the_period_progress_runs_from_the_best_before_to_the_best_in_it() {
        val start = T0 + (10 * day).seconds
        val sets =
            listOf(
                set(VISIT_A, 60.0, 10, 0),
                set(VISIT_A, 65.0, 8, 1),
                set(VISIT_B, 50.0, 10, 10 * day + 1),
                set(VISIT_B, 70.0, 6, 10 * day + 2),
            )

        assertEquals(
            MachineProgress(sets[1], sets[3], sinceBefore = true),
            machineProgress(sets, WeightMode.Total, start),
        )
    }

    @Test
    fun without_sets_before_the_period_progress_runs_from_its_worst_to_its_best() {
        val sets =
            listOf(
                set(VISIT_B, 60.0, 10, 1),
                set(VISIT_B, 50.0, 12, 2),
                set(VISIT_C, 70.0, 6, 3),
                set(VISIT_C, 99.0, 6, 4, deleted = true),
            )

        assertEquals(
            MachineProgress(sets[1], sets[2], sinceBefore = false),
            machineProgress(sets, WeightMode.Total, T0),
        )
    }

    @Test
    fun a_machine_without_sets_in_the_period_shows_no_progress() {
        val sets = listOf(set(VISIT_A, 60.0, 10, 0))

        assertNull(machineProgress(sets, WeightMode.Total, T0 + 1.hours))
    }

    @Test
    fun on_a_gravitron_less_weight_is_the_gain() {
        assertEquals(2.5, weightGain(30.0, 27.5, WeightMode.Counterweight))
        assertEquals(-2.5, weightGain(30.0, 32.5, WeightMode.Counterweight))
        assertEquals(5.0, weightGain(60.0, 65.0, WeightMode.Total))
    }

    @Test
    fun each_day_shows_its_best_set_oldest_first() {
        val utc: (Instant) -> Duration = { Duration.ZERO }
        val sets =
            listOf(
                set(VISIT_B, 70.0, 6, day + 1),
                set(VISIT_A, 60.0, 10, 0),
                set(VISIT_A, 65.0, 8, 1),
                set(VISIT_B, 70.0, 8, day + 2),
            )
        val first = CalendarDay.of(T0, Duration.ZERO)

        assertEquals(
            listOf(first to sets[2], first.plusDays(1) to sets[3]),
            bestPerDay(sets, WeightMode.Total, utc),
        )
    }
}
