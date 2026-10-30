package monster.greyde.kachalochka.ui.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

class DataStoreFeedPeoplePreference(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : FeedPeoplePreference {
    private val key = stringSetPreferencesKey("feed_selected_person")

    private val stored: Flow<Set<SelectedPerson>> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { selectedPeople(it[key].orEmpty()) }

    override val selected: StateFlow<Set<SelectedPerson>> =
        stored.stateIn(scope, SharingStarted.Eagerly, runBlocking { stored.first() })

    override suspend fun set(selected: Set<SelectedPerson>) {
        dataStore.edit { it[key] = selected.map { person -> person.stored() }.toSet() }
    }
}
