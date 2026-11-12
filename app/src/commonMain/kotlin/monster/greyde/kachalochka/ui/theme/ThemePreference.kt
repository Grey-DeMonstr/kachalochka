package monster.greyde.kachalochka.ui.theme

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

class ThemePreference(
    private val store: DeviceStore,
) : DeviceSetting<ThemeMode>(themeModeOrSystem(store.read(KEY))) {
    val mode: StateFlow<ThemeMode> = state

    override suspend fun save(value: ThemeMode) = store.write(KEY, value.name)

    private companion object {
        val KEY = DeviceKey.Text("theme_mode")
    }
}
