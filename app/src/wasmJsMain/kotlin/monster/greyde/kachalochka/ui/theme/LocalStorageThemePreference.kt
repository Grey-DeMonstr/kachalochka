package monster.greyde.kachalochka.ui.theme

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class LocalStorageThemePreference : ThemePreference {
    private val key = "theme_mode"
    private val state = MutableStateFlow(read())

    override val mode: Flow<ThemeMode> = state

    override suspend fun set(mode: ThemeMode) {
        runCatching { localStorage.setItem(key, mode.name) }
        state.value = mode
    }

    // A browser that refuses site storage throws on every access, so the choice lives as long as
    // the tab and no longer.
    private fun read(): ThemeMode =
        runCatching { themeModeOrSystem(localStorage.getItem(key)) }
            .getOrDefault(ThemeMode.System)
}
