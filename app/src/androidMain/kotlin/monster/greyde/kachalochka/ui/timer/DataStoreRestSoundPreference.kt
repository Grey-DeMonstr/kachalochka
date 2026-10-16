package monster.greyde.kachalochka.ui.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

class DataStoreRestSoundPreference(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : RestSoundPreference {
    private val enabledKey = booleanPreferencesKey("rest_sound")
    private val uriKey = stringPreferencesKey("rest_sound_uri")

    private val stored: Flow<RestSound> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { RestSound(it[enabledKey] ?: false, it[uriKey]) }

    override val sound: StateFlow<RestSound> =
        stored.stateIn(scope, SharingStarted.Eagerly, runBlocking { stored.first() })

    override suspend fun set(sound: RestSound) {
        dataStore.edit {
            it[enabledKey] = sound.enabled
            val uri = sound.uri
            if (uri == null) it.remove(uriKey) else it[uriKey] = uri
        }
    }
}
