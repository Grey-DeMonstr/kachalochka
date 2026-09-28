package monster.greyde.kachalochka.ui.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeasureFormatsTest {
    @Test
    fun a_value_reads_with_the_decimal_point_and_its_unit() {
        assertEquals("82.4 кг", measureValue(82.4, "кг"))
        assertEquals("18 %", measureValue(18.0, "%"))
        assertEquals("7", measureValue(7.0, ""))
    }

    @Test
    fun a_change_is_signed_with_a_true_minus_and_rounded_away_from_float_noise() {
        assertEquals("−0.4", measureDelta(82.0, 82.4))
        assertEquals("+1", measureDelta(83.0, 82.0))
        assertEquals("+0.1", measureDelta(0.3, 0.2))
    }

    @Test
    fun a_calculated_percent_keeps_exactly_one_decimal() {
        assertEquals("18.0", oneDecimal(18.0))
        assertEquals("14.7", oneDecimal(14.7392))
        assertEquals("20.1", oneDecimal(20.0996))
    }

    @Test
    fun an_unchanged_value_has_no_change_to_show() {
        assertNull(measureDelta(82.4, 82.4))
        assertNull(measureDelta(0.1 + 0.2, 0.3))
    }

    @Test
    fun a_chart_day_reads_as_the_day_and_the_short_month() {
        assertEquals("14 ноя", chartDayLabel(CalendarDay(2023, 11, 14)))
        assertEquals("1 мая", chartDayLabel(CalendarDay(2023, 5, 1)))
    }

    @Test
    fun the_chart_labels_about_four_days_across_its_span() {
        assertEquals(1, chartLabelSpacing(0))
        assertEquals(1, chartLabelSpacing(3))
        assertEquals(4, chartLabelSpacing(10))
        assertEquals(122, chartLabelSpacing(365))
    }
}
