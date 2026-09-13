package monster.greyde.kachalochka.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
        single<ThemePreference> { DataStoreThemePreference(get()) }
    }
