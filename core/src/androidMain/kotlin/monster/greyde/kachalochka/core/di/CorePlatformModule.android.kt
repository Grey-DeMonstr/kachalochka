package monster.greyde.kachalochka.core.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun corePlatformModule(): Module =
    module {
        includes(sqlModule())
        single<SqlDriver> {
            AndroidSqliteDriver(KachalochkaDatabase.Schema, androidContext(), "kachalochka.db")
        }
    }
