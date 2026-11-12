package monster.greyde.kachalochka.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException

class DataStoreDeviceStore(
    private val dataStore: DataStore<Preferences>,
) : DeviceStore {
    // Construction waits for the file, so that the Application pays for the read rather than the
    // first frame. An unreadable file leaves the defaults rather than a dead launch.
    @Volatile
    private var snapshot: Preferences =
        runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .first()
        }

    override fun <T : Any> read(key: DeviceKey<T>): T? = snapshot[key.preferencesKey()]

    override suspend fun <T : Any> write(
        key: DeviceKey<T>,
        value: T?,
    ) {
        val preferencesKey = key.preferencesKey()
        snapshot =
            dataStore.edit {
                if (value == null) it.remove(preferencesKey) else it[preferencesKey] = value
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> DeviceKey<T>.preferencesKey(): Preferences.Key<T> =
        when (this) {
            is DeviceKey.Text -> stringPreferencesKey(name)
            is DeviceKey.Number -> intPreferencesKey(name)
            is DeviceKey.Flag -> booleanPreferencesKey(name)
            is DeviceKey.TextSet -> stringSetPreferencesKey(name)
        } as Preferences.Key<T>
}
