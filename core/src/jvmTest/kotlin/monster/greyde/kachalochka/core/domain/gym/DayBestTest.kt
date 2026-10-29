package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayBestTest {
    @Test
    fun the_day_s_best_is_the_heaviest_set_with_the_most_reps_and_its_equal_sets_are_counted() {
        val sets =
            listOf(
                set(VISIT_A, 14.0, 10, atSeconds = 0),
                set(VISIT_A, 16.0, 10, atSeconds = 1),
                set(VISIT_A, 16.0, 10, atSeconds = 2),
                set(VISIT_A, 16.0, 8, atSeconds = 3),
                set(VISIT_A, 14.0, 10, atSeconds = 4),
            )

        assertEquals(DayBest(16.0, 10, 2), dayBest(sets, WeightMode.Total))
    }

    @Test
    fun deleted_sets_do_not_count_and_a_day_without_live_sets_has_no_best() {
        val sets =
            listOf(
                set(VISIT_A, 16.0, 10, atSeconds = 0),
                set(VISIT_A, 16.0, 10, atSeconds = 1, deleted = true),
            )

        assertEquals(DayBest(16.0, 10, 1), dayBest(sets, WeightMode.Total))
        assertNull(dayBest(listOf(sets[1]), WeightMode.Total))
    }

    @Test
    fun on_a_gravitron_the_lightest_set_is_the_best() {
        val sets =
            listOf(set(VISIT_A, 30.0, 8, atSeconds = 0), set(VISIT_A, 25.0, 8, atSeconds = 1))

        assertEquals(DayBest(25.0, 8, 1), dayBest(sets, WeightMode.Counterweight))
    }

    @Test
    fun results_compare_by_weight_then_reps_then_sets() {
        val order = dayBestOrder(WeightMode.Total)

        assertTrue(order.compare(DayBest(18.0, 6, 1), DayBest(16.0, 10, 2)) > 0)
        assertTrue(order.compare(DayBest(16.0, 12, 1), DayBest(16.0, 10, 2)) > 0)
        assertTrue(order.compare(DayBest(16.0, 10, 2), DayBest(16.0, 10, 1)) > 0)
        assertEquals(0, order.compare(DayBest(16.0, 10, 2), DayBest(16.0, 10, 2)))
        assertTrue(
            dayBestOrder(WeightMode.Counterweight)
                .compare(DayBest(25.0, 8, 1), DayBest(30.0, 8, 1)) > 0,
        )
    }
}
