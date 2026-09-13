package monster.greyde.kachalochka.ui.theme

import kotlinx.coroutines.flow.Flow

/** The theme is a device setting, not user data, so it never enters the sync model. */
interface ThemePreference {
    val mode: Flow<ThemeMode>

    suspend fun set(mode: ThemeMode)
}
