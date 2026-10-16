package monster.greyde.kachalochka.ui.timer

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The web plays one fixed beep, so only the switch is kept. */
class LocalStorageRestSoundPreference : RestSoundPreference {
    private val key = "rest_sound"
    private val state = MutableStateFlow(read())

    override val sound: StateFlow<RestSound> = state

    override suspend fun set(sound: RestSound) {
        runCatching { localStorage.setItem(key, sound.enabled.toString()) }
        state.value = RestSound(enabled = sound.enabled)
    }

    private fun read(): RestSound =
        RestSound(
            enabled = runCatching { localStorage.getItem(key) == "true" }.getOrDefault(false),
        )
}
