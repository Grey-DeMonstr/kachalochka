package monster.greyde.kachalochka.ui.share

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

class DataStoreShareGroupingPreference(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : ShareGroupingPreference {
    private val key = booleanPreferencesKey("share_group_by_tag")

    private val stored: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[key] ?: false }

    override val groupByTag: StateFlow<Boolean> =
        stored.stateIn(scope, SharingStarted.Eagerly, runBlocking { stored.first() })

    override suspend fun set(groupByTag: Boolean) {
        dataStore.edit { it[key] = groupByTag }
    }
}
