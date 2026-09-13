package monster.greyde.kachalochka.ui.theme

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryThemePreference(
    initial: ThemeMode = ThemeMode.System,
) : ThemePreference {
    private val state = MutableStateFlow(initial)

    override val mode: Flow<ThemeMode> = state

    override suspend fun set(mode: ThemeMode) {
        state.value = mode
    }
}
