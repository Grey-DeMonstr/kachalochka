package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.strings.inRussian
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FormatsTest {
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val press = Machine.new("Leg press", null, t0)
    private val kg = PreferredWeightUnit.Kg

    @Test
    fun numbers_use_a_decimal_point_and_drop_a_zero_fraction() {
        assertEquals("72.5", formatNumber(72.5))
        assertEquals("70", formatNumber(70.0))
        assertEquals("2.25", formatNumber(2.25))
    }

    @Test
    fun a_weekday_date_names_the_year_only_when_asked() {
        assertEquals(
            "Tuesday, 14 November",
            weekdayDate(CalendarDay(2023, 11, 14), withYear = false),
        )
        assertEquals(
            "Tuesday, 14 November 2023",
            weekdayDate(CalendarDay(2023, 11, 14), withYear = true),
        )
    }

    @Test
    fun the_weight_caption_names_unit_mode_and_step() {
        assertEquals("kg total · ±2.5", weightCaption(press, kg))
        assertEquals(
            "lb per side · ±5",
            weightCaption(
                press.copy(
                    unit = WeightUnit.Lb,
                    weightMode = WeightMode.PerSide,
                    weightStep = 5.0,
                ),
                PreferredWeightUnit.Mixed,
            ),
        )
    }

    @Test
    fun a_platform_outside_the_record_is_named_after_the_machine() {
        val sled = press.copy(platformWeight = 20.0)

        assertEquals("(+20 kg)", platformSuffix(sled, kg))
        assertNull(platformSuffix(sled.copy(platformIncluded = true), kg))
        assertNull(platformSuffix(press, kg))
    }

    @Test
    fun sets_read_as_weight_times_reps() {
        assertEquals("70 kg × 10", setValue(70.0, 10, "kg"))
    }

    @Test
    fun a_gravitron_set_and_caption_say_what_it_counts() {
        val gravitron = press.copy(weightMode = WeightMode.Counterweight)

        assertEquals("(-)27 kg × 10", setValue(27.0, 10, gravitron, kg))
        assertEquals("kg gravitron · ±2.5", weightCaption(gravitron, kg))
    }

    @Test
    fun an_own_unit_is_written_by_its_name() {
        val gravitron =
            press.copy(
                unit = WeightUnit.Custom,
                unitLabel = "plate",
                weightStep = 1.0,
                platformWeight = 2.0,
            )

        assertEquals("plate", unitLabel(gravitron))
        assertEquals("plate total · ±1", weightCaption(gravitron, kg))
        assertEquals("(+2 plate)", platformSuffix(gravitron, kg))
        assertEquals("u.", unitLabel(press.copy(unit = WeightUnit.Custom)))
        assertEquals("lb", unitLabel(press.copy(unit = WeightUnit.Lb, unitLabel = "plate")))
    }

    @Test
    fun counts_take_the_russian_plural() =
        inRussian {
            assertEquals("1 подход", setCount(1))
            assertEquals("3 подхода", setCount(3))
            assertEquals("6 подходов", setCount(6))
            assertEquals("11 подходов", setCount(11))
            assertEquals("22 подхода", setCount(22))
            assertEquals("3 упражнения", machineCount(3))
            assertEquals("5 упражнений", machineCount(5))
            assertEquals("21 упражнение", machineCount(21))
        }

    @Test
    fun members_are_counted_in_russian() =
        inRussian {
            assertEquals("1 участник", memberCount(1))
            assertEquals("3 участника", memberCount(3))
            assertEquals("11 участников", memberCount(11))
        }

    @Test
    fun days_ago_read_naturally() {
        assertEquals("today", daysAgoLabel(0))
        assertEquals("yesterday", daysAgoLabel(1))
        assertEquals("4 days ago", daysAgoLabel(4))
        inRussian {
            assertEquals("4 дня назад", daysAgoLabel(4))
            assertEquals("5 дней назад", daysAgoLabel(5))
        }
    }

    @Test
    fun a_date_reads_as_day_and_month_with_the_year_only_when_it_differs() {
        assertEquals("7 November", dayMonthLabel(CalendarDay(2023, 11, 7), currentYear = 2023))
        assertEquals("1 January 2022", dayMonthLabel(CalendarDay(2022, 1, 1), currentYear = 2023))
    }

    @Test
    fun times_are_formatted_like_the_design() {
        assertEquals("1:30", formatRest(90.seconds))
        assertEquals("0:05", formatRest(4.2.seconds))
    }

    @Test
    fun calendar_labels_name_the_month_and_the_weekdays() {
        assertEquals("November 2023", monthTitle(CalendarMonth(2023, 11)))
        assertEquals("Tuesday", weekdayName(2))
        assertEquals("Su", weekdayLabels().last())
        assertEquals("2023-11-05", isoDate(CalendarDay(2023, 11, 5)))
    }

    @Test
    fun a_monogram_is_the_first_letter_uppercased() {
        assertEquals("I", monogram("Ivan"))
        assertEquals("М", monogram("миша"))
        assertEquals("?", monogram("  "))
    }

    @Test
    fun a_typed_number_takes_a_comma_or_a_point() {
        assertEquals(22.5, parseDecimal("22,5"))
        assertEquals(22.5, parseDecimal(" 22.5 "))
        assertEquals(22.0, parseDecimal("22."))
        assertEquals(0.5, parseDecimal(",5"))
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("7,,5"))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("NaN"))
    }

    @Test
    fun a_typed_number_rejects_scientific_and_hex_notation() {
        assertNull(parseDecimal("1e308"))
        assertNull(parseDecimal("1e3"))
        assertNull(parseDecimal("22d"))
        assertNull(parseDecimal("0x1p3"))
    }
}
