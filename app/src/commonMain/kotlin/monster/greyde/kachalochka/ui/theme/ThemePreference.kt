package monster.greyde.kachalochka.ui.theme

import kotlinx.coroutines.flow.StateFlow

interface ThemePreference {
    // A StateFlow so the first frame is painted from the stored mode instead of flashing the
    // system scheme until the first emission arrives.
    val mode: StateFlow<ThemeMode>

    suspend fun set(mode: ThemeMode)
}
