package monster.greyde.kachalochka.ui.timer

import androidx.compose.runtime.Composable

/** The platform's own sounds for the end of a rest. */
interface RestSoundChooser {
    /** Null where the platform plays one fixed sound. */
    @Composable
    fun rememberLaunchers(onPicked: (String?) -> Unit): RestSoundLaunchers?
}

/** [String] arguments and results are [RestSound.uri] values. */
class RestSoundLaunchers(
    val pick: (current: String?) -> Unit,
    /** Asks for the right to show the rest as a notification, where the platform needs it. */
    val allowNotifications: () -> Unit,
    val nameOf: (uri: String?) -> String,
)

object FixedRestSound : RestSoundChooser {
    @Composable
    override fun rememberLaunchers(onPicked: (String?) -> Unit): RestSoundLaunchers? = null
}
