package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Duration
import kotlin.time.Instant

private const val MILLIS_PER_DAY = 86_400_000L
private const val MILLIS_PER_MINUTE = 60_000L

fun calendarDaysBetween(
    earlier: Instant,
    later: Instant,
    utcOffset: Duration,
): Int = (localDay(later, utcOffset) - localDay(earlier, utcOffset)).toInt()

fun minuteOfDay(
    instant: Instant,
    utcOffset: Duration,
): Int = (localMillis(instant, utcOffset).mod(MILLIS_PER_DAY) / MILLIS_PER_MINUTE).toInt()

private fun localDay(
    instant: Instant,
    utcOffset: Duration,
): Long = localMillis(instant, utcOffset).floorDiv(MILLIS_PER_DAY)

private fun localMillis(
    instant: Instant,
    utcOffset: Duration,
): Long = instant.toEpochMilliseconds() + utcOffset.inWholeMilliseconds
