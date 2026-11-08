package monster.greyde.kachalochka.ui.network

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

class DataStoreProxyPreference(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : ProxyPreference {
    private val addressKey = stringPreferencesKey("proxy_address")
    private val usedKey = booleanPreferencesKey("proxy_used")

    private val stored: Flow<ProxySetting> =
        dataStore.data
            // An unreadable file leaves the app without a proxy rather than without a launch.
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { ProxySetting(it[addressKey].orEmpty(), it[usedKey] ?: false) }

    // Every client of the app is built with the stored proxy, so the first read must wait for it.
    override val setting: StateFlow<ProxySetting> =
        stored.stateIn(scope, SharingStarted.Eagerly, runBlocking { stored.first() })

    override suspend fun set(setting: ProxySetting) {
        dataStore.edit {
            it[addressKey] = setting.address
            it[usedKey] = setting.used
        }
    }
}
