package monster.greyde.kachalochka.ui.calendar

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

class DataStoreVisitsModePreference(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : VisitsModePreference {
    private val key = stringPreferencesKey("visits_mode")

    private val stored: Flow<VisitsMode> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { visitsModeOrCalendar(it[key]) }

    override val mode: StateFlow<VisitsMode> =
        stored.stateIn(scope, SharingStarted.Eagerly, runBlocking { stored.first() })

    override suspend fun set(mode: VisitsMode) {
        dataStore.edit { it[key] = mode.name }
    }
}
