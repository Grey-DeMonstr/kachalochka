package monster.greyde.kachalochka.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import monster.greyde.kachalochka.ui.theme.DataStoreThemePreference
import monster.greyde.kachalochka.ui.theme.ThemePreference
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<DataStore<Preferences>> {
            val context: Context = androidContext()
            PreferenceDataStoreFactory.createWithPath(
                produceFile = {
                    context.filesDir
                        .resolve("theme.preferences_pb")
                        .absolutePath
                        .toPath()
                },
            )
        }
        // Built while Koin starts, so the stored mode is in hand before the first Activity.
        single<ThemePreference>(createdAtStart = true) {
            DataStoreThemePreference(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }
    }
