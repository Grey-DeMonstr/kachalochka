package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** The device's clock with its offset from UTC, which together give the device's calendar day. */
class DeviceClock(
    private val clock: Clock,
    private val utcOffset: UtcOffset,
) : Clock {
    override fun now(): Instant = clock.now()

    fun offsetAt(instant: Instant): Duration = utcOffset.at(instant)

    fun today(): CalendarDay {
        val now = now()
        return CalendarDay.of(now, offsetAt(now))
    }
}
