package monster.greyde.kachalochka.ui.strings

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

class LanguagePreference(
    private val store: DeviceStore,
) : DeviceSetting<AppLanguage>(languageOrSystem(store.read(KEY))) {
    val language: StateFlow<AppLanguage> = state

    override suspend fun save(value: AppLanguage) = store.write(KEY, value.name)

    private companion object {
        val KEY = DeviceKey.Text("language")
    }
}

/** The device's language tag, such as "ru-RU". */
fun interface SystemLanguage {
    fun tag(): String
}
