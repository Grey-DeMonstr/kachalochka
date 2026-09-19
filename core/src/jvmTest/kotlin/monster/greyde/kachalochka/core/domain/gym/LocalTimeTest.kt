package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LocalTimeTest {
    // 2023-11-14T22:13:20Z
    private val lateEvening = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun days_are_counted_on_the_local_calendar() {
        val nextMorningUtc = lateEvening + 3.hours

        assertEquals(1, calendarDaysBetween(lateEvening, nextMorningUtc, Duration.ZERO))
        // At UTC+3 both instants fall on the 15th.
        assertEquals(0, calendarDaysBetween(lateEvening, nextMorningUtc, 3.hours))
    }

    @Test
    fun the_minute_of_the_day_follows_the_offset() {
        assertEquals(22 * 60 + 13, minuteOfDay(lateEvening, Duration.ZERO))
        assertEquals(1 * 60 + 13, minuteOfDay(lateEvening, 3.hours))
    }
}
