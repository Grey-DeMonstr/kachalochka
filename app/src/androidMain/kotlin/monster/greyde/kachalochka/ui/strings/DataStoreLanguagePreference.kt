package monster.greyde.kachalochka.ui.strings

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

class DataStoreLanguagePreference(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : LanguagePreference {
    private val key = stringPreferencesKey("language")

    private val stored: Flow<AppLanguage> =
        dataStore.data
            // The language is a device setting, so an unreadable file falls back to the system's.
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { languageOrSystem(it[key]) }

    // Read while Koin starts, so the first frame is already in the chosen language.
    override val language: StateFlow<AppLanguage> =
        stored.stateIn(scope, SharingStarted.Eagerly, runBlocking { stored.first() })

    override suspend fun set(language: AppLanguage) {
        dataStore.edit { it[key] = language.name }
    }
}
