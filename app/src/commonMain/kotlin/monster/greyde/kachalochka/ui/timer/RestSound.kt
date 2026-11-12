package monster.greyde.kachalochka.ui.timer

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

/** The sound at the end of a rest. [uri] is the Android sound chosen; null is the default one. */
data class RestSound(
    val enabled: Boolean = false,
    val uri: String? = null,
)

/** A device setting, as the theme is. */
class RestSoundPreference(
    private val store: DeviceStore,
) : DeviceSetting<RestSound>(RestSound(store.read(ENABLED) ?: false, store.read(URI))) {
    val sound: StateFlow<RestSound> = state

    override suspend fun save(value: RestSound) {
        store.write(ENABLED, value.enabled)
        store.write(URI, value.uri)
    }

    private companion object {
        val ENABLED = DeviceKey.Flag("rest_sound")
        val URI = DeviceKey.Text("rest_sound_uri")
    }
}
