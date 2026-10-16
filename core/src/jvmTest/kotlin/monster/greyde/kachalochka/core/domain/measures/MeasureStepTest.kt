package monster.greyde.kachalochka.core.domain.measures

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasureStepTest {
    @Test
    fun a_measure_without_values_steps_by_one() {
        assertEquals(1.0, measureStep(emptyList()))
    }

    @Test
    fun whole_values_step_by_one() {
        assertEquals(1.0, measureStep(listOf(82.0, 90.0, 0.0)))
    }

    @Test
    fun a_half_steps_by_half() {
        assertEquals(0.5, measureStep(listOf(82.0, 81.5, 90.0)))
    }

    @Test
    fun any_other_fraction_steps_by_a_tenth() {
        assertEquals(0.1, measureStep(listOf(81.5, 74.4, 90.0)))
        assertEquals(0.1, measureStep(listOf(74.25)))
    }

    @Test
    fun a_value_read_back_with_a_rounding_error_keeps_its_precision() {
        assertEquals(1.0, measureStep(listOf(0.1 + 0.2 + 0.7)))
        assertEquals(0.5, measureStep(listOf(0.1 * 3 + 0.2)))
    }
}
