package monster.greyde.kachalochka.ui.format

import kotlin.time.Duration
import kotlin.time.Instant

/** The device's offset from UTC at an instant; `kotlin.time` has no time zones of its own. */
fun interface UtcOffset {
    fun at(instant: Instant): Duration
}

expect fun platformUtcOffset(instant: Instant): Duration
