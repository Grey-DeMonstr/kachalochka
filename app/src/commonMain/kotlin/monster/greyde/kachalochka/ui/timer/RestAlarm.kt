package monster.greyde.kachalochka.ui.timer

import kotlin.time.Instant

/** Plays the rest's sound at its end, also with the app in the background where it can. */
interface RestAlarm {
    /** Replaces the earlier schedule. [sound] is a [RestSound.uri]. */
    fun schedule(
        end: Instant,
        sound: String?,
    )

    fun cancel()
}

object NoRestAlarm : RestAlarm {
    override fun schedule(
        end: Instant,
        sound: String?,
    ) = Unit

    override fun cancel() = Unit
}
