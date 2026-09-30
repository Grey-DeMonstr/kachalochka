package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class WeightUnitsTest {
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val kgPress = Machine.new("Жим ногами", null, t0)
    private val lbCable =
        Machine.new("Кроссовер", null, t0).copy(unit = WeightUnit.Lb, weightStep = 5.0)
    private val gravitron =
        Machine
            .new("Гравитрон", null, t0)
            .copy(unit = WeightUnit.Custom, unitLabel = "плитка", weightStep = 1.0)

    @Test
    fun kilograms_or_pounds_replace_the_machine_s_unit_and_mixed_keeps_it() {
        val kg = PreferredWeightUnit.Kg
        val lb = PreferredWeightUnit.Lb
        val mixed = PreferredWeightUnit.Mixed
        assertEquals(WeightUnit.Kg, shownUnit(WeightUnit.Lb, kg))
        assertEquals(WeightUnit.Lb, shownUnit(WeightUnit.Kg, lb))
        assertEquals(WeightUnit.Lb, shownUnit(WeightUnit.Lb, mixed))
        assertEquals(WeightUnit.Kg, shownUnit(WeightUnit.Kg, mixed))
        PreferredWeightUnit.entries.forEach {
            assertEquals(WeightUnit.Custom, shownUnit(WeightUnit.Custom, it))
        }
    }

    @Test
    fun a_converted_weight_rounds_to_the_nearest_half_unit() {
        assertEquals(41.0, shownWeight(90.0, WeightUnit.Lb, WeightUnit.Kg))
        assertEquals(20.5, shownWeight(45.0, WeightUnit.Lb, WeightUnit.Kg))
        assertEquals(88.0, shownWeight(40.0, WeightUnit.Kg, WeightUnit.Lb))
        assertEquals(99.0, shownWeight(45.0, WeightUnit.Kg, WeightUnit.Lb))
    }

    @Test
    fun a_weight_left_in_its_unit_is_not_rounded() {
        assertEquals(72.25, shownWeight(72.25, WeightUnit.Kg, WeightUnit.Kg))
        assertEquals(7.3, shownWeight(7.3, WeightUnit.Custom, WeightUnit.Custom))
    }

    @Test
    fun a_converted_step_keeps_one_decimal() {
        assertEquals(2.3, shownStep(5.0, WeightUnit.Lb, WeightUnit.Kg))
        assertEquals(5.5, shownStep(2.5, WeightUnit.Kg, WeightUnit.Lb))
        assertEquals(2.25, shownStep(2.25, WeightUnit.Kg, WeightUnit.Kg))
    }

    @Test
    fun a_machine_s_caption_names_the_shown_unit_and_the_converted_step() {
        assertEquals("кг всего · ±2.3", weightCaption(lbCable, PreferredWeightUnit.Kg))
        assertEquals("lb всего · ±5.5", weightCaption(kgPress, PreferredWeightUnit.Lb))
        assertEquals("lb всего · ±5", weightCaption(lbCable, PreferredWeightUnit.Mixed))
        assertEquals("плитка всего · ±1", weightCaption(gravitron, PreferredWeightUnit.Lb))
    }

    @Test
    fun a_weight_recorded_in_another_unit_is_converted_to_the_chosen_one() {
        assertEquals("41 кг", recordingConversion(lbCable, 90.0, PreferredWeightUnit.Kg))
        assertEquals("88 lb", recordingConversion(kgPress, 40.0, PreferredWeightUnit.Lb))
    }

    @Test
    fun a_weight_recorded_in_the_chosen_unit_or_an_own_one_needs_no_conversion() {
        assertEquals(null, recordingConversion(lbCable, 90.0, PreferredWeightUnit.Mixed))
        assertEquals(null, recordingConversion(lbCable, 90.0, PreferredWeightUnit.Lb))
        assertEquals(null, recordingConversion(kgPress, 40.0, PreferredWeightUnit.Kg))
        assertEquals(null, recordingConversion(gravitron, 7.0, PreferredWeightUnit.Kg))
    }

    @Test
    fun a_set_and_a_platform_read_in_the_shown_unit() {
        val kg = PreferredWeightUnit.Kg
        assertEquals("41 кг × 8", setValue(90.0, 8, lbCable, kg))
        assertEquals("90 lb × 8", setValue(90.0, 8, lbCable, PreferredWeightUnit.Mixed))
        assertEquals("(+45.5 кг)", platformSuffix(lbCable.copy(platformWeight = 100.0), kg))
        assertEquals(
            "Жим ногами (+44 lb)",
            machineTitle(kgPress.copy(platformWeight = 20.0), PreferredWeightUnit.Lb),
        )
    }
}
