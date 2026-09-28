package monster.greyde.kachalochka.ui.measures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeasureFormatsTest {
    @Test
    fun a_value_reads_with_the_decimal_comma_and_its_unit() {
        assertEquals("82,4 кг", measureValue(82.4, "кг"))
        assertEquals("18 %", measureValue(18.0, "%"))
        assertEquals("7", measureValue(7.0, ""))
    }

    @Test
    fun a_change_is_signed_with_a_true_minus_and_rounded_away_from_float_noise() {
        assertEquals("−0,4", measureDelta(82.0, 82.4))
        assertEquals("+1", measureDelta(83.0, 82.0))
        assertEquals("+0,1", measureDelta(0.3, 0.2))
    }

    @Test
    fun an_unchanged_value_has_no_change_to_show() {
        assertNull(measureDelta(82.4, 82.4))
        assertNull(measureDelta(0.1 + 0.2, 0.3))
    }
}
