package monster.greyde.kachalochka.core.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/** The JVM target ships nothing; its driver is in memory because only host tests reach it. */
actual fun corePlatformModule(): Module =
    module {
        includes(sqlModule())
        single<SqlDriver> {
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(KachalochkaDatabase.Schema::create)
        }
    }
