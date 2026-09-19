package monster.greyde.kachalochka.core.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

internal fun inMemoryDatabase(): KachalochkaDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    KachalochkaDatabase.Schema.create(driver)
    return kachalochkaDatabase(driver)
}
