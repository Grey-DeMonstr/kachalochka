package monster.greyde.kachalochka.ui.format

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private fun timezoneOffsetMinutes(epochMillis: Double): Int =
    js("new Date(epochMillis).getTimezoneOffset()")

// getTimezoneOffset counts minutes from local time to UTC, so east of Greenwich it is negative.
actual fun platformUtcOffset(instant: Instant): Duration =
    (-timezoneOffsetMinutes(instant.toEpochMilliseconds().toDouble())).minutes
