package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals

class PyramidTest {
    private fun step(
        weight: Double,
        at: Long,
        pyramid: Boolean = false,
    ) = set(VISIT_A, weight, atSeconds = at).copy(pyramid = pyramid)

    @Test
    fun sets_without_a_start_stand_alone() {
        val sets = listOf(step(50.0, 0), step(55.0, 1))

        assertEquals(
            listOf(SetGroup(sets.take(1), false), SetGroup(sets.drop(1), false)),
            pyramidGroups(sets),
        )
    }

    @Test
    fun a_pyramid_holds_its_start_and_the_sets_up_to_the_next_start() {
        val warmUp = step(50.0, 0)
        val first = listOf(step(40.0, 1, pyramid = true), step(31.0, 2), step(22.0, 3))
        val second = listOf(step(36.0, 4, pyramid = true), step(27.0, 5))

        assertEquals(
            listOf(SetGroup(listOf(warmUp), false), SetGroup(first, true), SetGroup(second, true)),
            pyramidGroups(listOf(warmUp) + first + second),
        )
    }

    @Test
    fun a_new_pyramid_starts_from_the_first_set_of_the_last_one() {
        val sets = listOf(step(50.0, 0), step(40.0, 1, pyramid = true), step(31.0, 2))

        assertEquals(sets[1], lastPyramidStart(sets))
        assertEquals(null, lastPyramidStart(sets.take(1)))
    }
}
