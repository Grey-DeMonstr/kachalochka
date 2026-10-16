package monster.greyde.kachalochka.fakes

import androidx.compose.runtime.Composable
import monster.greyde.kachalochka.ui.timer.RestAlarm
import monster.greyde.kachalochka.ui.timer.RestSoundChooser
import monster.greyde.kachalochka.ui.timer.RestSoundLaunchers
import kotlin.time.Instant

class RecordingRestAlarm : RestAlarm {
    val scheduled = mutableListOf<Pair<Instant, String?>>()
    var cancels = 0

    override fun schedule(
        end: Instant,
        sound: String?,
    ) {
        scheduled += end to sound
    }

    override fun cancel() {
        cancels++
    }
}

/** Picks [picked] at once, as the Android picker would; a picked sound's name is its uri. */
class InstantRestSoundChooser(
    private val picked: String,
) : RestSoundChooser {
    var notificationAsks = 0

    @Composable
    override fun rememberLaunchers(onPicked: (String?) -> Unit) =
        RestSoundLaunchers(
            pick = { onPicked(picked) },
            allowNotifications = { notificationAsks++ },
            nameOf = { it ?: "По умолчанию" },
        )
}
