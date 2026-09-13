package monster.greyde.kachalochka.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreThemePreference(
    private val dataStore: DataStore<Preferences>,
) : ThemePreference {
    private val key = stringPreferencesKey("theme_mode")

    override val mode: Flow<ThemeMode> =
        dataStore.data
            // An unreadable preferences file reaches the collector as an IOException. The theme
            // is a device setting, so the default scheme is a better answer than a dead launch.
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { themeModeOrSystem(it[key]) }

    override suspend fun set(mode: ThemeMode) {
        dataStore.edit { it[key] = mode.name }
    }
}
