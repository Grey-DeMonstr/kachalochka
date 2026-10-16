package monster.greyde.kachalochka.ui.timer

import kotlin.time.Clock
import kotlin.time.Instant

private const val BEEP_HERTZ = 880
private const val BEEP_SECONDS = 0.2

/** A short beep through the Web Audio API, while the tab is open. */
class WebAudioRestAlarm : RestAlarm {
    private var timeout: Int? = null

    override fun schedule(
        end: Instant,
        sound: String?,
    ) {
        cancel()
        val delay = (end - Clock.System.now()).inWholeMilliseconds.coerceAtLeast(0L)
        timeout = beepAfter(delay.toDouble(), BEEP_HERTZ, BEEP_SECONDS)
    }

    override fun cancel() {
        timeout?.let(::clearBeep)
        timeout = null
    }
}

// One audio context for the page: browsers limit how many a page may open. It is woken at the
// start of the rest, closest to the tap, since a browser may keep audio asleep until a tap.
@OptIn(ExperimentalWasmJsInterop::class)
@Suppress("UNUSED_PARAMETER")
private fun beepAfter(
    delayMillis: Double,
    hertz: Int,
    seconds: Double,
): Int =
    js(
        """{
        const Context = window.AudioContext || window.webkitAudioContext;
        if (!Context) return 0;
        const audio = globalThis.kachalochkaAudio || (globalThis.kachalochkaAudio = new Context());
        audio.resume().catch(() => {});
        return setTimeout(() => {
            audio.resume().then(() => {
                const start = audio.currentTime;
                const tone = audio.createOscillator();
                const gain = audio.createGain();
                tone.frequency.value = hertz;
                gain.gain.setValueAtTime(0.0001, start);
                gain.gain.exponentialRampToValueAtTime(0.3, start + 0.01);
                gain.gain.exponentialRampToValueAtTime(0.0001, start + seconds);
                tone.connect(gain);
                gain.connect(audio.destination);
                tone.start(start);
                tone.stop(start + seconds);
            }).catch(() => {});
        }, delayMillis);
    }""",
    )

@OptIn(ExperimentalWasmJsInterop::class)
@Suppress("UNUSED_PARAMETER")
private fun clearBeep(id: Int): Unit = js("clearTimeout(id)")
