package monster.greyde.kachalochka.core.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each test rebuilds the tables its migration changes as the previous schema version declared
 * them, migrates one step and reads the columns back with raw SQL, which later columns cannot
 * break.
 */
class SchemaMigrationTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private fun exec(sql: String) {
        driver.execute(null, sql, 0)
    }

    private fun machineColumns(id: String): List<Any?> =
        driver
            .executeQuery(
                null,
                "SELECT weight_mode, unit_label, updated_at FROM machine WHERE id = '$id'",
                { cursor ->
                    cursor.next()
                    QueryResult.Value(
                        listOf(cursor.getString(0), cursor.getString(1), cursor.getLong(2)),
                    )
                },
                0,
            ).value

    @Test
    fun version_2_machines_gain_an_empty_unit_name_and_lose_counterweight() {
        KachalochkaDatabase.Schema.create(driver)
        exec("DROP TABLE machine")
        exec(
            """
            CREATE TABLE machine (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT,
                name TEXT NOT NULL,
                setup_note TEXT NOT NULL DEFAULT '',
                weight_mode TEXT NOT NULL,
                platform_weight REAL NOT NULL DEFAULT 0,
                platform_included INTEGER NOT NULL DEFAULT 0,
                unit TEXT NOT NULL,
                weight_step REAL NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        exec(
            "INSERT INTO machine(id, name, weight_mode, unit, weight_step, updated_at) " +
                "VALUES ('gravitron', 'Гравитрон', 'counterweight', 'kg', 2.5, 7)",
        )
        exec(
            "INSERT INTO machine(id, name, weight_mode, unit, weight_step, updated_at) " +
                "VALUES ('press', 'Жим ногами', 'per_side', 'lb', 5, 7)",
        )

        KachalochkaDatabase.Schema.migrate(driver, 2, 3)

        assertEquals(listOf<Any?>("total", "", 7L), machineColumns("gravitron"))
        assertEquals(listOf<Any?>("per_side", "", 7L), machineColumns("press"))
    }
}
