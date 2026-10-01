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
    fun the_first_set_takes_the_heaviest_set_of_the_previous_visit_with_its_most_reps() {
        val previous =
            listOf(
                set(VISIT_A, 70.0, 10, 0),
                set(VISIT_A, 75.0, 8, 60),
                set(VISIT_A, 75.0, 9, 120),
                set(VISIT_A, 72.5, 12, 180),
            )

        assertEquals(SetValues(75.0, 9), suggestNextSet(machine(), previous, emptyList()))
    }

    @Test
    fun on_a_gravitron_the_first_set_takes_the_lightest_set_of_the_previous_visit() {
        val gravitron = machine().copy(weightMode = WeightMode.Counterweight)
        val previous =
            listOf(
                set(VISIT_A, 30.0, 8, 0),
                set(VISIT_A, 25.0, 6, 60),
                set(VISIT_A, 25.0, 7, 120),
                set(VISIT_A, 27.5, 10, 180),
            )

        assertEquals(SetValues(25.0, 7), suggestNextSet(gravitron, previous, emptyList()))
    }

    @Test
    fun a_later_set_repeats_the_weight_and_the_reps_just_recorded() {
        val today = listOf(set(VISIT_B, 60.0, 12, 1), set(VISIT_B, 72.5, 6, 2))

        assertEquals(SetValues(72.5, 6), suggestNextSet(machine(), yesterday, today))
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
