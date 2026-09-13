package monster.greyde.kachalochka.core.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Only host tests reach the JVM target, so its driver lives in memory. */
actual fun corePlatformModule(): Module =
    module {
        includes(sqlModule())
        single<SqlDriver> {
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(KachalochkaDatabase.Schema::create)
        }
    }
