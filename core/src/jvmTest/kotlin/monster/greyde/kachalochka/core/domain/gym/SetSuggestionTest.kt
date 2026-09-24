package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class SetSuggestionTest {
    private val yesterday =
        listOf(set(VISIT_A, 70.0, 10, 0), set(VISIT_A, 70.0, 10, 60), set(VISIT_A, 75.0, 8, 120))

    @Test
    fun previous_visit_sets_are_those_of_the_latest_other_visit() {
        val older = set(VISIT_C, 50.0, atSeconds = -86_400)
        val today = set(VISIT_B, 60.0, atSeconds = 86_400)

        assertEquals(
            yesterday,
            previousVisitSets(listOf(today, older) + yesterday.reversed(), VISIT_B),
        )
    }

    @Test
    fun deleted_sets_do_not_count_as_a_previous_visit() {
        val deleted = set(VISIT_C, 99.0, atSeconds = 500, deleted = true)

        assertEquals(yesterday, previousVisitSets(yesterday + deleted, VISIT_B))
    }

    @Test
    fun only_sets_before_the_bound_count_as_the_previous_visit() {
        val older = listOf(set(VISIT_A, 50.0, atSeconds = 0))
        val later = listOf(set(VISIT_B, 70.0, atSeconds = 86_400))

        assertEquals(
            older,
            previousVisitSets(older + later, VISIT_C, before = T0 + 1.hours),
        )
    }

    @Test
    fun the_first_set_takes_the_first_set_of_the_previous_visit() {
        assertEquals(SetValues(70.0, 10), suggestNextSet(machine(), yesterday, emptyList()))
    }

    @Test
    fun the_nth_set_takes_the_nth_set_of_the_previous_visit() {
        val today = listOf(set(VISIT_B, 60.0, atSeconds = 1), set(VISIT_B, 70.0, atSeconds = 2))

        assertEquals(SetValues(75.0, 8), suggestNextSet(machine(), yesterday, today))
    }

    @Test
    fun past_the_previous_visit_the_last_set_of_this_visit_repeats() {
        val today =
            listOf(60.0, 70.0, 70.0).mapIndexed { i, w -> set(VISIT_B, w, atSeconds = i + 1L) }

        assertEquals(SetValues(70.0, 10), suggestNextSet(machine(), yesterday, today))
    }

    @Test
    fun a_first_ever_set_starts_at_zero_times_ten() {
        assertEquals(
            SetValues(0.0, DEFAULT_REPS),
            suggestNextSet(machine(), emptyList(), emptyList()),
        )
    }

    @Test
    fun a_first_ever_set_starts_at_an_included_platform_weight() {
        val sled = machine(platformWeight = 25.0, platformIncluded = true)

        assertEquals(SetValues(25.0, 10), suggestNextSet(sled, emptyList(), emptyList()))
    }
}
