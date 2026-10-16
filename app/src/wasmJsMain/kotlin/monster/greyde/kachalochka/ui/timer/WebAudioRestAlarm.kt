package monster.greyde.kachalochka.ui.timer

import kotlin.time.Clock
import kotlin.time.Instant

private const val BEEP_HERTZ = 880
private const val BEEP_SECONDS = 0.2

/** A short beep through the Web Audio API, while the tab is open. */
class WebAudioRestAlarm : RestAlarm {
    private var timeout: Int? = null

    init {
        wakeAudioOnGesture()
    }

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

// One audio context for the page: browsers limit how many a page may open. Safari starts audio
// only inside a gesture's handler, so every tap wakes the context the beep plays on later.
@OptIn(ExperimentalWasmJsInterop::class)
private fun wakeAudioOnGesture(): Unit =
    js(
        """{
        const wake = () => {
            const Context = window.AudioContext || window.webkitAudioContext;
            if (!Context) return;
            const audio =
                globalThis.kachalochkaAudio || (globalThis.kachalochkaAudio = new Context());
            if (audio.state !== 'running') audio.resume().catch(() => {});
        };
        document.addEventListener('pointerdown', wake, { capture: true, passive: true });
        document.addEventListener('touchend', wake, { capture: true, passive: true });
    }""",
    )

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
