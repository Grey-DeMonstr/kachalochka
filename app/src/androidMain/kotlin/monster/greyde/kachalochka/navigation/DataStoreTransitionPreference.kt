package monster.greyde.kachalochka.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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

class DataStoreTransitionPreference(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : TransitionPreference {
    private val key = intPreferencesKey("transition_millis")

    private val stored: Flow<Int> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[key] ?: DEFAULT_TRANSITION_MILLIS }

    override val millis: StateFlow<Int> =
        stored.stateIn(scope, SharingStarted.Eagerly, runBlocking { stored.first() })

    override suspend fun set(millis: Int) {
        dataStore.edit { it[key] = millis }
    }
}
