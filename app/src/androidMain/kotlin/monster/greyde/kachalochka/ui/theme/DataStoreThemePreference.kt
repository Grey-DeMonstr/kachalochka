package monster.greyde.kachalochka.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import java.io.IOException

class DataStoreThemePreference(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : ThemePreference {
    private val key = stringPreferencesKey("theme_mode")

    private val stored: Flow<ThemeMode> =
        dataStore.data
            // An unreadable preferences file reaches the collector as an IOException. The theme
            // is a device setting, so the default scheme is a better answer than a dead launch.
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { themeModeOrSystem(it[key]) }

    // Construction is what waits for the file, so that the Application pays for the read rather
    // than the first frame.
    override val mode: StateFlow<ThemeMode> =
        stored.stateIn(scope, SharingStarted.Eagerly, runBlocking { stored.first() })

    override suspend fun set(mode: ThemeMode) {
        dataStore.edit { it[key] = mode.name }
    }
}
