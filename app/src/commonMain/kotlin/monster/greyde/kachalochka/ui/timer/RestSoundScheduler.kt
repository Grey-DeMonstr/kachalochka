package monster.greyde.kachalochka.ui.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.Koin
import kotlin.time.Clock

/** Keeps the [RestAlarm] on the end of the running rest while the sound is on. */
class RestSoundScheduler(
    private val timer: RestTimer,
    private val sounds: RestSoundPreference,
    private val alarm: RestAlarm,
    private val clock: Clock,
) {
    suspend fun run() {
        var scheduled = false
        combine(timer.startedAt, sounds.sound, ::Pair).collect { (startedAt, sound) ->
            val end = startedAt?.plus(timer.duration)
            if (!sound.enabled) {
                if (scheduled) alarm.cancel()
                scheduled = false
            } else if (end != null && end > clock.now()) {
                alarm.schedule(end, sound.uri)
                scheduled = true
            }
        }
    }
}

fun Koin.startRestSound() {
    val scheduler = get<RestSoundScheduler>()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { scheduler.run() }
}
