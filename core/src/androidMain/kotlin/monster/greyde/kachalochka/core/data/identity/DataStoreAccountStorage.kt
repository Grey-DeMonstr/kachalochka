package monster.greyde.kachalochka.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException

class DataStoreAccountStorage(
    private val dataStore: DataStore<Preferences>,
) : AccountStorage {
    private val key = stringPreferencesKey("accounts")

    // Blocking, so the store that calls it is built while Koin starts rather than on a frame.
    override fun read(): String? =
        runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .first()[key]
        }

    override suspend fun write(value: String) {
        dataStore.edit { it[key] = value }
    }
}
