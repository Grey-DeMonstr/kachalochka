package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val lateEvening = Instant.parse("2023-11-14T23:30:00Z")

private object Fixed : Clock {
    override fun now(): Instant = lateEvening
}

class DeviceClockTest {
    @Test
    fun today_is_the_device_s_day_at_its_offset() {
        val east = DeviceClock(Fixed) { 1.hours }
        val west = DeviceClock(Fixed) { (-1).hours }

        assertEquals(CalendarDay(2023, 11, 15), east.today())
        assertEquals(CalendarDay(2023, 11, 14), west.today())
    }

    @Test
    fun the_offset_is_the_device_s_at_the_given_instant() {
        val clock = DeviceClock(Fixed) { at -> if (at < lateEvening) 2.hours else 1.hours }

        assertEquals(2.hours, clock.offsetAt(Instant.parse("2023-07-01T12:00:00Z")))
        assertEquals(1.hours, clock.offsetAt(clock.now()))
    }
}
