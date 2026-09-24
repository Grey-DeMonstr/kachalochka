package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FormatsTest {
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val press = Machine.new("Жим ногами", null, t0)

    private fun set(weight: Double) =
        WorkoutSet(
            WorkoutSetId.random(),
            null,
            VisitId.random(),
            press.id,
            weight,
            10,
            t0,
            t0,
            false,
        )

    @Test
    fun numbers_use_a_decimal_comma_and_drop_a_zero_fraction() {
        assertEquals("72,5", formatNumber(72.5))
        assertEquals("70", formatNumber(70.0))
        assertEquals("2,25", formatNumber(2.25))
    }

    @Test
    fun the_weight_caption_names_unit_mode_and_step() {
        assertEquals("кг всего · ±2,5", weightCaption(press))
        assertEquals(
            "lb на сторону · ±5",
            weightCaption(
                press.copy(
                    unit = WeightUnit.Lb,
                    weightMode = WeightMode.PerSide,
                    weightStep = 5.0,
                ),
            ),
        )
        assertEquals(
            "кг противовес · ±2,5",
            weightCaption(press.copy(weightMode = WeightMode.Counterweight)),
        )
    }

    @Test
    fun a_platform_outside_the_record_is_named_after_the_machine() {
        val sled = press.copy(platformWeight = 20.0)

        assertEquals("(+20 кг)", platformSuffix(sled))
        assertEquals("Жим ногами (+20 кг)", machineTitle(sled))
        assertNull(platformSuffix(sled.copy(platformIncluded = true)))
        assertNull(platformSuffix(press))
        assertEquals("Жим ногами", machineTitle(press))
    }

    @Test
    fun sets_read_as_weight_times_reps() {
        assertEquals("70 кг × 10", setValue(70.0, 10, WeightUnit.Kg))
        assertEquals("72,5×8", shortSet(72.5, 8))
    }

    @Test
    fun a_group_of_equal_weights_is_counted_and_others_are_listed() {
        assertEquals("2 × 45 кг", groupSummary(listOf(set(45.0), set(45.0)), WeightUnit.Kg))
        assertEquals(
            "60, 70, 70 кг",
            groupSummary(listOf(set(60.0), set(70.0), set(70.0)), WeightUnit.Kg),
        )
        assertEquals("45 кг", groupSummary(listOf(set(45.0)), WeightUnit.Kg))
    }

    @Test
    fun counts_take_the_russian_plural() {
        assertEquals("1 подход", setCount(1))
        assertEquals("3 подхода", setCount(3))
        assertEquals("6 подходов", setCount(6))
        assertEquals("11 подходов", setCount(11))
        assertEquals("22 подхода", setCount(22))
        assertEquals("3 тренажёра", machineCount(3))
        assertEquals("5 тренажёров", machineCount(5))
        assertEquals("21 тренажёр", machineCount(21))
    }

    @Test
    fun days_ago_read_naturally() {
        assertEquals("сегодня", daysAgoLabel(0))
        assertEquals("вчера", daysAgoLabel(1))
        assertEquals("4 дня назад", daysAgoLabel(4))
        assertEquals("5 дней назад", daysAgoLabel(5))
    }

    @Test
    fun a_date_reads_as_day_and_month_with_the_year_only_when_it_differs() {
        assertEquals("7 ноября", dayMonthLabel(CalendarDay(2023, 11, 7), currentYear = 2023))
        assertEquals("1 января 2022", dayMonthLabel(CalendarDay(2022, 1, 1), currentYear = 2023))
        assertEquals("мая", monthGenitive(5))
    }

    @Test
    fun times_are_formatted_like_the_design() {
        assertEquals("19:52", clockLabel(19 * 60 + 52))
        assertEquals("07:05", clockLabel(7 * 60 + 5))
        assertEquals("1:30", formatRest(90.seconds))
        assertEquals("0:05", formatRest(4.2.seconds))
    }

    @Test
    fun calendar_labels_are_russian() {
        assertEquals("Ноябрь 2023", monthTitle(CalendarMonth(2023, 11)))
        assertEquals("Вторник", weekdayName(2))
        assertEquals("Вс", WEEKDAY_LABELS.last())
        assertEquals("2023-11-05", isoDate(CalendarDay(2023, 11, 5)))
    }

    @Test
    fun a_monogram_is_the_first_letter_uppercased() {
        assertEquals("I", monogram("Ivan"))
        assertEquals("М", monogram("миша"))
        assertEquals("?", monogram("  "))
    }
}
