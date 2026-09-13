package monster.greyde.kachalochka.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreThemePreference(
    private val dataStore: DataStore<Preferences>,
) : ThemePreference {
    private val key = stringPreferencesKey("theme_mode")

    override val mode: Flow<ThemeMode> =
        dataStore.data.map { themeModeOrSystem(it[key]) }

    override suspend fun set(mode: ThemeMode) {
        dataStore.edit { it[key] = mode.name }
    }
}
