package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

val REST_DURATION: Duration = 90.seconds

/** An idle or finished timer reads as the full [duration], ready for the next rest. */
fun restRemaining(
    startedAt: Instant?,
    duration: Duration,
    now: Instant,
): Duration {
    if (startedAt == null) return duration
    val left = duration - (now - startedAt)
    return if (left <= Duration.ZERO) duration else left.coerceAtMost(duration)
}
