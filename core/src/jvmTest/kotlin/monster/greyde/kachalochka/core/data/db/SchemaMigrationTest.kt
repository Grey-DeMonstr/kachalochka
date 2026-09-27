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

    private fun number(sql: String): Long? =
        driver
            .executeQuery(
                null,
                sql,
                { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0))
                },
                0,
            ).value

    private fun text(sql: String): String? =
        driver
            .executeQuery(
                null,
                sql,
                { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getString(0))
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

    @Test
    fun version_3_visits_gain_an_empty_day_and_sets_a_zero_position() {
        KachalochkaDatabase.Schema.create(driver)
        exec("DROP TABLE visit")
        exec("DROP TABLE workout_set")
        exec(
            """
            CREATE TABLE visit (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT,
                recorded_at INTEGER NOT NULL,
                ended_at INTEGER,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        exec(
            """
            CREATE TABLE workout_set (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT,
                visit_id TEXT NOT NULL,
                machine_id TEXT NOT NULL,
                weight REAL NOT NULL,
                reps INTEGER NOT NULL,
                recorded_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        exec(
            "INSERT INTO visit(id, recorded_at, ended_at, updated_at) " +
                "VALUES ('sunday', 5, 6, 7)",
        )
        exec(
            "INSERT INTO workout_set(id, visit_id, machine_id, weight, reps, recorded_at, " +
                "updated_at) VALUES ('first', 'sunday', 'press', 70, 10, 5, 7)",
        )

        KachalochkaDatabase.Schema.migrate(driver, 3, 4)

        assertEquals(null, text("SELECT day FROM visit WHERE id = 'sunday'"))
        assertEquals(7L, number("SELECT updated_at FROM visit WHERE id = 'sunday'"))
        assertEquals(0L, number("SELECT position FROM workout_set WHERE id = 'first'"))
        assertEquals(
            "visit_day_idx",
            text("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'visit_day_idx'"),
        )
    }
}
