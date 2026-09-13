package monster.greyde.kachalochka.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryThemePreference(
    initial: ThemeMode = ThemeMode.System,
) : ThemePreference {
    private val state = MutableStateFlow(initial)

    override val mode: StateFlow<ThemeMode> = state

    override suspend fun set(mode: ThemeMode) {
        state.value = mode
    }
}
