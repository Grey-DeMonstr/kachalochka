package monster.greyde.kachalochka.ui.format

import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

actual fun platformUtcOffset(instant: Instant): Duration =
    TimeZone.getDefault().getOffset(instant.toEpochMilliseconds()).milliseconds
