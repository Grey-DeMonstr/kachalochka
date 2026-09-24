package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class CalendarDayTest {
    // 2023-11-14T22:13:20Z, a Tuesday
    private val lateEvening = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun the_epoch_is_day_zero_and_a_thursday() {
        assertEquals(0L, CalendarDay(1970, 1, 1).epochDay)
        assertEquals(4, CalendarDay(1970, 1, 1).dayOfWeek)
        assertEquals(-1L, CalendarDay(1969, 12, 31).epochDay)
    }

    @Test
    fun epoch_days_round_trip_across_leap_years() {
        listOf(
            CalendarDay(2000, 2, 29),
            CalendarDay(1900, 3, 1),
            CalendarDay(2024, 12, 31),
            CalendarDay(1969, 12, 31),
        ).forEach { assertEquals(it, CalendarDay.ofEpochDay(it.epochDay)) }
    }

    @Test
    fun an_instant_falls_on_its_local_day() {
        assertEquals(CalendarDay(2023, 11, 14), CalendarDay.of(lateEvening, Duration.ZERO))
        assertEquals(CalendarDay(2023, 11, 15), CalendarDay.of(lateEvening, 3.hours))
        assertEquals(2, CalendarDay(2023, 11, 14).dayOfWeek)
    }

    @Test
    fun a_local_time_of_day_maps_back_to_its_instant() {
        assertEquals(
            Instant.parse("2023-11-10T09:00:00Z"),
            CalendarDay(2023, 11, 10).at(12.hours.inWholeMilliseconds, 3.hours),
        )
        assertEquals((22 * 3600 + 13 * 60 + 20) * 1000L, millisOfDay(lateEvening, Duration.ZERO))
    }

    @Test
    fun days_step_and_compare_in_calendar_order() {
        assertEquals(CalendarDay(2024, 1, 1), CalendarDay(2023, 12, 31).plusDays(1))
        assertTrue(CalendarDay(2023, 12, 1) > CalendarDay(2023, 11, 30))
    }

    @Test
    fun february_follows_the_gregorian_leap_rule() {
        assertEquals(29, CalendarMonth(2024, 2).length)
        assertEquals(28, CalendarMonth(2023, 2).length)
        assertEquals(28, CalendarMonth(1900, 2).length)
        assertEquals(29, CalendarMonth(2000, 2).length)
    }

    @Test
    fun a_month_is_laid_out_in_monday_first_weeks() {
        val weeks = CalendarMonth(2023, 11).weeks()

        assertEquals(5, weeks.size)
        assertEquals(listOf(null, null, 1, 2, 3, 4, 5), weeks.first().map { it?.day })
        assertEquals(listOf(27, 28, 29, 30, null, null, null), weeks.last().map { it?.day })
    }

    @Test
    fun months_step_across_years() {
        assertEquals(CalendarMonth(2024, 1), CalendarMonth(2023, 12).plusMonths(1))
        assertEquals(CalendarMonth(2022, 12), CalendarMonth(2023, 1).plusMonths(-1))
        assertEquals(CalendarMonth(2023, 11), CalendarMonth.of(CalendarDay(2023, 11, 14)))
    }
}
