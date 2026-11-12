package monster.greyde.kachalochka.navigation

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

class TransitionPreference(
    private val store: DeviceStore,
) : DeviceSetting<Int>(store.read(KEY) ?: DEFAULT_TRANSITION_MILLIS) {
    val millis: StateFlow<Int> = state

    override suspend fun save(value: Int) = store.write(KEY, value)

    private companion object {
        val KEY = DeviceKey.Number("transition_millis")
    }
}
