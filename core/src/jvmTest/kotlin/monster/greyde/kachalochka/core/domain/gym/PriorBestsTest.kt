package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class PriorBestsTest {
    private val d1 = CalendarDay(2023, 11, 1)
    private val d2 = CalendarDay(2023, 11, 2)
    private val d3 = CalendarDay(2023, 11, 3)
    private val days = mapOf(VISIT_A to d1, VISIT_B to d2, VISIT_C to d3)

    private fun withBests(
        sets: List<WorkoutSet>,
        mode: WeightMode = WeightMode.Total,
        days: Map<VisitId, CalendarDay> = this.days,
    ) = withPriorBests(sets, days::get, mode, T0 + 1.hours)

    private fun List<WorkoutSet>.priorsOf(visit: VisitId) =
        filter { it.visitId == visit }.map { it.priorBest }.toSet()

    @Test
    fun each_day_carries_the_best_of_the_earlier_days_and_the_first_day_carries_none() {
        val sets =
            listOf(
                set(VISIT_A, 14.0, 10, atSeconds = 0),
                set(VISIT_A, 16.0, 10, atSeconds = 1),
                set(VISIT_A, 14.0, 10, atSeconds = 2),
                set(VISIT_B, 14.0, 10, atSeconds = 10),
                set(VISIT_B, 16.0, 10, atSeconds = 11),
                set(VISIT_B, 16.0, 10, atSeconds = 12),
            )

        val rows = withBests(sets)

        assertEquals(6, rows.size)
        assertEquals(setOf(DayBest.NONE), rows.priorsOf(VISIT_A))
        assertEquals(setOf(DayBest(16.0, 10, 1)), rows.priorsOf(VISIT_B))
        assertEquals(setOf(T0 + 1.hours), rows.map { it.updatedAt }.toSet())
    }

    @Test
    fun a_second_run_over_the_result_writes_nothing() {
        val sets =
            listOf(set(VISIT_A, 16.0, 10, atSeconds = 0), set(VISIT_B, 18.0, 10, atSeconds = 1))

        val first = withBests(sets)

        assertEquals(emptyList(), withPriorBests(first, days::get, WeightMode.Total, T0 + 2.hours))
    }

    @Test
    fun the_running_best_keeps_the_greatest_day_not_the_latest() {
        val sets =
            listOf(
                set(VISIT_A, 20.0, 10, atSeconds = 0),
                set(VISIT_B, 16.0, 10, atSeconds = 1),
                set(VISIT_C, 18.0, 10, atSeconds = 2),
            )

        val rows = withBests(sets)

        assertEquals(setOf(DayBest(20.0, 10, 1)), rows.priorsOf(VISIT_B))
        assertEquals(setOf(DayBest(20.0, 10, 1)), rows.priorsOf(VISIT_C))
    }

    @Test
    fun two_visits_on_one_day_count_as_one_day() {
        val sameDay = days + (VISIT_C to d1)
        val sets =
            listOf(
                set(VISIT_A, 16.0, 10, atSeconds = 0),
                set(VISIT_C, 16.0, 10, atSeconds = 1),
                set(VISIT_B, 16.0, 10, atSeconds = 2),
            )

        val rows = withBests(sets, days = sameDay)

        assertEquals(setOf(DayBest.NONE), rows.priorsOf(VISIT_A) + rows.priorsOf(VISIT_C))
        assertEquals(setOf(DayBest(16.0, 10, 2)), rows.priorsOf(VISIT_B))
    }

    @Test
    fun deleted_sets_are_skipped_and_never_rewritten() {
        val gone = set(VISIT_A, 100.0, 10, atSeconds = 0, deleted = true)
        val sets =
            listOf(
                gone,
                set(VISIT_A, 16.0, 10, atSeconds = 1),
                set(VISIT_B, 18.0, 10, atSeconds = 2),
            )

        val rows = withBests(sets)

        assertEquals(2, rows.size)
        assertEquals(setOf(DayBest(16.0, 10, 1)), rows.priorsOf(VISIT_B))
    }

    @Test
    fun a_set_whose_visit_has_no_day_is_left_out() {
        val undated = set(VISIT_C, 100.0, 10, atSeconds = 0)
        val sets =
            listOf(
                undated,
                set(VISIT_A, 16.0, 10, atSeconds = 1),
                set(VISIT_B, 18.0, 10, atSeconds = 2),
            )

        val rows = withBests(sets, days = days - VISIT_C)

        assertEquals(2, rows.size)
        assertEquals(setOf(DayBest(16.0, 10, 1)), rows.priorsOf(VISIT_B))
    }

    @Test
    fun on_a_gravitron_the_first_day_still_carries_none_and_a_lighter_day_raises_the_best() {
        val sets =
            listOf(
                set(VISIT_A, 30.0, 8, atSeconds = 0),
                set(VISIT_B, 25.0, 8, atSeconds = 1),
                set(VISIT_C, 28.0, 8, atSeconds = 2),
            )

        val rows = withBests(sets, mode = WeightMode.Counterweight)

        assertEquals(setOf(DayBest.NONE), rows.priorsOf(VISIT_A))
        assertEquals(setOf(DayBest(30.0, 8, 1)), rows.priorsOf(VISIT_B))
        assertEquals(setOf(DayBest(25.0, 8, 1)), rows.priorsOf(VISIT_C))
    }

    @Test
    fun a_stored_value_that_is_already_right_is_not_returned() {
        val right = set(VISIT_A, 16.0, 10, atSeconds = 0).copy(priorBest = DayBest.NONE)
        val wrong = set(VISIT_B, 18.0, 10, atSeconds = 1).copy(priorBest = DayBest.NONE)
        val alsoRight =
            set(
                VISIT_C,
                20.0,
                10,
                atSeconds = 2,
            ).copy(priorBest = DayBest(18.0, 10, 1))

        val rows = withBests(listOf(right, wrong, alsoRight))

        assertEquals(listOf(wrong.id), rows.map { it.id })
        assertEquals(DayBest(16.0, 10, 1), rows.single().priorBest)
    }
}
