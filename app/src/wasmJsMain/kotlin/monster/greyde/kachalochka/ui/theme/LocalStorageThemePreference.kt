package monster.greyde.kachalochka.ui.theme

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class LocalStorageThemePreference : ThemePreference {
    private val key = "theme_mode"
    private val state = MutableStateFlow(read())

    override val mode: Flow<ThemeMode> = state

    override suspend fun set(mode: ThemeMode) {
        localStorage.setItem(key, mode.name)
        state.value = mode
    }

    private fun read(): ThemeMode {
        val stored = localStorage.getItem(key) ?: return ThemeMode.System
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.System
    }
}
