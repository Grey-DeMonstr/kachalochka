package monster.greyde.kachalochka.ui.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sound at the end of a rest. [uri] is the Android sound chosen; null is the default one. */
data class RestSound(
    val enabled: Boolean = false,
    val uri: String? = null,
)

/** A device setting, as the theme is. */
interface RestSoundPreference {
    val sound: StateFlow<RestSound>

    suspend fun set(sound: RestSound)
}

class InMemoryRestSoundPreference : RestSoundPreference {
    private val state = MutableStateFlow(RestSound())

    override val sound: StateFlow<RestSound> = state

    override suspend fun set(sound: RestSound) {
        state.value = sound
    }
}
