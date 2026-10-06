package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class UnitConversionTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val press = Machine.new("Жим ногами", ivan, T0)
    private val now = T0 + 1.days

    @Test
    fun only_kilograms_and_pounds_convert_into_each_other() {
        assertTrue(convertible(WeightUnit.Kg, WeightUnit.Lb))
        assertTrue(convertible(WeightUnit.Lb, WeightUnit.Kg))
        assertFalse(convertible(WeightUnit.Kg, WeightUnit.Kg))
        assertFalse(convertible(WeightUnit.Custom, WeightUnit.Kg))
        assertFalse(convertible(WeightUnit.Lb, WeightUnit.Custom))
    }

    @Test
    fun pounds_become_kilograms_to_a_tenth() {
        assertEquals(45.4, convertedWeight(100.0, WeightUnit.Lb, WeightUnit.Kg, lbStep = 5.0))
    }

    @Test
    fun kilograms_become_the_nearest_pounds_on_the_step() {
        assertEquals(50.0, convertedWeight(22.7, WeightUnit.Kg, WeightUnit.Lb, lbStep = 5.0))
        assertEquals(45.0, convertedWeight(20.0, WeightUnit.Kg, WeightUnit.Lb, lbStep = 2.5))
        assertEquals(43.75, convertedWeight(20.0, WeightUnit.Kg, WeightUnit.Lb, lbStep = 1.25))
    }

    @Test
    fun an_own_unit_never_converts() {
        assertEquals(7.0, convertedWeight(7.0, WeightUnit.Custom, WeightUnit.Kg, lbStep = 5.0))
        assertEquals(7.0, convertedWeight(7.0, WeightUnit.Kg, WeightUnit.Kg, lbStep = 5.0))
    }

    @Test
    fun the_pound_step_is_the_largest_that_fits_every_recorded_weight() {
        assertEquals(5.0, guessedLbStep(listOf(22.7, 27.2, 31.8), kgStep = 2.5))
        assertEquals(2.5, guessedLbStep(listOf(22.7, 21.5), kgStep = 2.5))
        assertEquals(1.25, guessedLbStep(listOf(22.7, 20.98), kgStep = 2.5))
    }

    @Test
    fun without_recorded_weights_the_pound_step_is_the_nearest_to_the_kilogram_one() {
        assertEquals(5.0, guessedLbStep(emptyList(), kgStep = 2.5))
        assertEquals(2.5, guessedLbStep(emptyList(), kgStep = 1.0))
        assertEquals(1.25, guessedLbStep(emptyList(), kgStep = 0.5))
    }

    @Test
    fun converted_sets_keep_everything_but_the_weight_and_the_update_time() {
        val recorded =
            WorkoutSet(
                WorkoutSetId.random(),
                ivan,
                VisitId.random(),
                press.id,
                22.7,
                10,
                0,
                T0,
                T0,
                false,
            )

        assertEquals(
            listOf(recorded.copy(weight = 50.0, updatedAt = now)),
            convertedSets(listOf(recorded), WeightUnit.Kg, WeightUnit.Lb, lbStep = 5.0, now),
        )
    }

    @Test
    fun a_platform_shift_compares_the_platforms_in_the_new_unit() {
        val kg = press.copy(platformWeight = 20.0)
        val lb = press.copy(unit = WeightUnit.Lb, platformWeight = 45.0, weightStep = 5.0)

        assertEquals(0.0, platformShift(from = kg, to = lb))
        assertEquals(0.0, platformShift(from = lb, to = kg.copy(platformWeight = 20.4)))
    }
}
