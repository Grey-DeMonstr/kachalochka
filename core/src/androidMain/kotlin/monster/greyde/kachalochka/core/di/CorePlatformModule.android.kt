package monster.greyde.kachalochka.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.identity.AccountStorage
import monster.greyde.kachalochka.core.data.identity.DataStoreAccountStorage
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun corePlatformModule(): Module =
    module {
        includes(sqlModule())
        single<SqlDriver> {
            AndroidSqliteDriver(KachalochkaDatabase.Schema, androidContext(), "kachalochka.db")
        }
        single<DataStore<Preferences>>(named("accounts")) {
            val context: Context = androidContext()
            PreferenceDataStoreFactory.createWithPath(
                produceFile = {
                    context.filesDir
                        .resolve("accounts.preferences_pb")
                        .absolutePath
                        .toPath()
                },
            )
        }
        single<AccountStorage> { DataStoreAccountStorage(get(named("accounts"))) }
    }
