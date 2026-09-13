package monster.greyde.kachalochka.ui.theme

import kotlinx.coroutines.flow.Flow

interface ThemePreference {
    val mode: Flow<ThemeMode>

    suspend fun set(mode: ThemeMode)
}
