package monster.greyde.kachalochka.core.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import monster.greyde.kachalochka.core.data.gym.visitOf
import monster.greyde.kachalochka.core.data.gym.workoutSetOf
import monster.greyde.kachalochka.core.data.profile.profileOf
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import monster.greyde.kachalochka.core.domain.profile.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Each test rebuilds the tables its migration changes as the previous schema version declared
 * them. A one-step test reads the columns back with raw SQL, which later columns cannot break; a
 * test migrating to [KachalochkaDatabase.Schema.version] reads through the generated queries, as
 * the app does, and so must also rebuild every table a later migration changes.
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

    private fun version3VisitTables() {
        KachalochkaDatabase.Schema.create(driver)
        exec("DROP TABLE visit")
        exec("DROP TABLE workout_set")
        exec("DROP TABLE machine")
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
                deleted INTEGER NOT NULL DEFAULT 0,
                unit_label TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        version5ProfileTable()
        exec("DROP TABLE machine_link")
        exec("DROP TABLE measure")
        exec("DROP TABLE measurement")
        exec("DROP TABLE photo")
        exec("DROP TABLE workout_plan")
    }

    /** Every version before 6 declares the profile table this way. */
    private fun version5ProfileTable() {
        exec("DROP TABLE profile")
        exec(
            """
            CREATE TABLE profile (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT,
                display_name TEXT,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    @Test
    fun version_3_visits_gain_an_empty_day_and_sets_a_zero_position() {
        version3VisitTables()
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

    @Test
    fun a_version_3_visit_and_its_set_read_back_whole_on_the_current_schema() {
        val owner = UserId("11111111-1111-4111-8111-111111111111")
        val visit = Visit(VISIT, owner, null, at(5), at(7), false)
        val set = WorkoutSet(SET, owner, VISIT, PRESS, 70.5, 10, 0, at(6), at(7), false)
        version3VisitTables()
        exec(
            "INSERT INTO visit(id, user_id, recorded_at, ended_at, updated_at) " +
                "VALUES ('${VISIT.value}', '${owner.value}', 5, 5, 7)",
        )
        exec(
            "INSERT INTO workout_set(id, user_id, visit_id, machine_id, weight, reps, " +
                "recorded_at, updated_at) VALUES ('${SET.value}', '${owner.value}', " +
                "'${VISIT.value}', '${PRESS.value}', 70.5, 10, 6, 7)",
        )

        KachalochkaDatabase.Schema.migrate(driver, 3, KachalochkaDatabase.Schema.version)

        val database = kachalochkaDatabase(driver)
        assertEquals(visit, database.visitQueries.byId(VISIT.value, ::visitOf).executeAsOne())
        assertEquals(
            listOf(set),
            database.workoutSetQueries.forVisit(VISIT.value, ::workoutSetOf).executeAsList(),
        )
    }

    @Test
    fun a_version_3_profile_reads_back_whole_on_the_current_schema() {
        val owner = UserId("11111111-1111-4111-8111-111111111111")
        val profile = Profile(ProfileId(owner.value), owner, "Иван", at(7), false)
        version3VisitTables()
        exec(
            "INSERT INTO profile(id, user_id, display_name, updated_at) " +
                "VALUES ('${owner.value}', '${owner.value}', 'Иван', 7)",
        )

        KachalochkaDatabase.Schema.migrate(driver, 3, KachalochkaDatabase.Schema.version)

        val database = kachalochkaDatabase(driver)
        assertEquals(
            profile,
            database.profileQueries.byId(owner.value, ::profileOf).executeAsOne(),
        )
    }

    @Test
    fun version_4_machines_gain_an_empty_link_and_every_row_is_pulled_again() {
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
                deleted INTEGER NOT NULL DEFAULT 0,
                unit_label TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        exec(
            "INSERT INTO machine(id, name, weight_mode, unit, weight_step, updated_at, " +
                "unit_label) VALUES ('press', 'Жим ногами', 'total', 'custom', 5, 7, 'плитка')",
        )
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 4, 5)

        assertEquals(null, text("SELECT link_id FROM machine WHERE id = 'press'"))
        assertEquals("плитка", text("SELECT unit_label FROM machine WHERE id = 'press'"))
        assertEquals(7L, number("SELECT updated_at FROM machine WHERE id = 'press'"))
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_5_profiles_gain_empty_body_fields_and_every_row_is_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        version5ProfileTable()
        exec(
            "INSERT INTO profile(id, user_id, display_name, updated_at) " +
                "VALUES ('ivan', 'ivan', 'Иван', 7)",
        )
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 5, 6)

        assertEquals("{}", text("SELECT friend_colors FROM profile WHERE id = 'ivan'"))
        assertEquals(null, text("SELECT sex FROM profile WHERE id = 'ivan'"))
        assertEquals(null, number("SELECT birth_year FROM profile WHERE id = 'ivan'"))
        assertEquals(null, text("SELECT height_cm FROM profile WHERE id = 'ivan'"))
        assertEquals("Иван", text("SELECT display_name FROM profile WHERE id = 'ivan'"))
        assertEquals(7L, number("SELECT updated_at FROM profile WHERE id = 'ivan'"))
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_6_gains_the_machine_link_table_and_every_row_is_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        exec("DROP TABLE machine_link")
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 6, 7)

        exec(
            "INSERT INTO machine_link(id, user_id, machine_id, linked_machine_id, updated_at) " +
                "VALUES ('link', 'ivan', 'mine', 'theirs', 7)",
        )
        assertEquals(
            "theirs",
            text("SELECT linked_machine_id FROM machine_link WHERE id = 'link'"),
        )
        assertEquals(0L, number("SELECT deleted FROM machine_link WHERE id = 'link'"))
        assertEquals(
            "machine_link_updated_at_idx",
            text(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'index' AND name = 'machine_link_updated_at_idx'",
            ),
        )
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_7_gains_the_measure_tables_and_every_row_is_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        exec("DROP TABLE measure")
        exec("DROP TABLE measurement")
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 7, 8)

        exec(
            "INSERT INTO measure(id, user_id, name, updated_at) " +
                "VALUES ('neck', 'ivan', 'Шея', 7)",
        )
        exec(
            "INSERT INTO measurement(id, user_id, measure_id, day, value, updated_at) " +
                "VALUES ('monday', 'ivan', 'neck', '2026-09-21', 38.5, 7)",
        )
        assertEquals("", text("SELECT unit FROM measure WHERE id = 'neck'"))
        assertEquals(null, text("SELECT kind FROM measure WHERE id = 'neck'"))
        assertEquals(0L, number("SELECT position FROM measure WHERE id = 'neck'"))
        assertEquals(0L, number("SELECT deleted FROM measure WHERE id = 'neck'"))
        assertEquals("2026-09-21", text("SELECT day FROM measurement WHERE id = 'monday'"))
        assertEquals(0L, number("SELECT deleted FROM measurement WHERE id = 'monday'"))
        assertEquals(
            listOf("measure_updated_at_idx", "measurement_updated_at_idx"),
            listOf("measure", "measurement").map {
                text(
                    "SELECT name FROM sqlite_master " +
                        "WHERE type = 'index' AND name = '${it}_updated_at_idx'",
                )
            },
        )
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_8_profiles_trade_the_birth_year_for_its_first_day_and_are_pulled_again() {
        val owner = UserId("11111111-1111-4111-8111-111111111111")
        version8ProfileTable()
        exec(
            "INSERT INTO profile(id, user_id, display_name, updated_at, friend_colors, sex, " +
                "birth_year, height_cm) VALUES ('${owner.value}', '${owner.value}', 'Иван', 7, " +
                "'{}', 'male', 1990, 180.5)",
        )
        exec(
            "INSERT INTO profile(id, updated_at) VALUES ('22222222-2222-4222-8222-222222222222', 8)",
        )
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 8, KachalochkaDatabase.Schema.version)

        val database = kachalochkaDatabase(driver)
        assertEquals(
            Profile(
                ProfileId(owner.value),
                owner,
                "Иван",
                at(7),
                false,
                sex = Sex.Male,
                birthDate = CalendarDay(1990, 1, 1),
                heightCm = 180.5,
            ),
            database.profileQueries.byId(owner.value, ::profileOf).executeAsOne(),
        )
        assertEquals(
            null,
            database.profileQueries
                .byId("22222222-2222-4222-8222-222222222222", ::profileOf)
                .executeAsOne()
                .birthDate,
        )
        assertEquals(
            "profile_updated_at_idx",
            text(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'index' AND name = 'profile_updated_at_idx'",
            ),
        )
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_9_profiles_show_weights_in_kilograms_and_are_pulled_again() {
        val owner = UserId("11111111-1111-4111-8111-111111111111")
        version9ProfileTable()
        exec(
            "INSERT INTO profile(id, user_id, display_name, updated_at, friend_colors, sex, " +
                "birth_date, height_cm) VALUES ('${owner.value}', '${owner.value}', 'Иван', 7, " +
                "'{}', 'male', '1990-06-15', 180.5)",
        )
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 9, KachalochkaDatabase.Schema.version)

        assertEquals("kg", text("SELECT weight_unit FROM profile WHERE id = '${owner.value}'"))
        assertEquals(
            Profile(
                ProfileId(owner.value),
                owner,
                "Иван",
                at(7),
                false,
                sex = Sex.Male,
                birthDate = CalendarDay(1990, 6, 15),
                heightCm = 180.5,
                weightUnit = PreferredWeightUnit.Kg,
            ),
            kachalochkaDatabase(driver)
                .profileQueries
                .byId(owner.value, ::profileOf)
                .executeAsOne(),
        )
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_10_gains_the_photo_table_and_every_row_is_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        exec("DROP TABLE photo")
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 10, 11)

        exec(
            "INSERT INTO photo(id, user_id, machine_id, taken_at, updated_at) " +
                "VALUES ('front', 'ivan', 'press', 5, 7)",
        )
        assertEquals("press", text("SELECT machine_id FROM photo WHERE id = 'front'"))
        assertEquals(0L, number("SELECT deleted FROM photo WHERE id = 'front'"))
        assertEquals(
            listOf("photo_updated_at_idx", "photo_machine_id_idx"),
            listOf("updated_at", "machine_id").map {
                text(
                    "SELECT name FROM sqlite_master " +
                        "WHERE type = 'index' AND name = 'photo_${it}_idx'",
                )
            },
        )
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_11_sets_gain_an_empty_comment_and_every_row_is_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        version11SetTable()
        exec(
            "INSERT INTO workout_set VALUES ('${SET.value}', NULL, '${VISIT.value}', " +
                "'${PRESS.value}', 80.0, 8, 1, 1, 0, 0)",
        )
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 11, 12)

        assertEquals("", text("SELECT comment FROM workout_set WHERE id = '${SET.value}'"))
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_12_machines_gain_no_tags_and_profiles_no_grouping_and_are_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        version12MachineTable()
        version12ProfileTable()
        exec(
            "INSERT INTO machine(id, name, weight_mode, unit, weight_step, updated_at) " +
                "VALUES ('press', 'Жим', 'total', 'kg', 2.5, 1)",
        )
        exec("INSERT INTO profile(id, updated_at) VALUES ('me', 1)")
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 12, 13)

        assertEquals("[]", text("SELECT tags FROM machine WHERE id = 'press'"))
        assertEquals(0L, number("SELECT group_by_tag FROM profile WHERE id = 'me'"))
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    /** Versions 4 to 13 declare the visit table this way. */
    private fun version13VisitTable() {
        exec("DROP TABLE visit")
        exec(
            """
            CREATE TABLE visit (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT,
                recorded_at INTEGER NOT NULL,
                ended_at INTEGER,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                day TEXT
            )
            """.trimIndent(),
        )
    }

    @Test
    fun version_13_visits_plan_nothing_and_plans_arrive_and_everything_is_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        version13VisitTable()
        exec("DROP TABLE workout_plan")
        exec(
            "INSERT INTO visit(id, recorded_at, updated_at, day) " +
                "VALUES ('sunday', 5, 7, '2026-09-27')",
        )
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 13, 14)

        exec(
            "INSERT INTO workout_plan(id, user_id, created_at, updated_at) " +
                "VALUES ('legs', 'ivan', 5, 7)",
        )
        assertEquals("[]", text("SELECT planned FROM visit WHERE id = 'sunday'"))
        assertEquals("[]", text("SELECT machine_ids FROM workout_plan WHERE id = 'legs'"))
        assertEquals("", text("SELECT name FROM workout_plan WHERE id = 'legs'"))
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_14_profiles_choose_no_avatar_and_are_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        version12ProfileTable()
        exec("ALTER TABLE profile ADD COLUMN group_by_tag INTEGER NOT NULL DEFAULT 0")
        exec("INSERT INTO profile(id, updated_at) VALUES ('me', 1)")
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 14, 15)

        assertEquals(null, text("SELECT avatar_photo FROM profile WHERE id = 'me'"))
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_15_profiles_sort_machines_by_recent_use_and_are_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        version12ProfileTable()
        exec("ALTER TABLE profile ADD COLUMN group_by_tag INTEGER NOT NULL DEFAULT 0")
        exec("ALTER TABLE profile ADD COLUMN avatar_photo TEXT")
        exec("INSERT INTO profile(id, updated_at) VALUES ('me', 1)")
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 15, 16)

        assertEquals("recent", text("SELECT machine_sort FROM profile WHERE id = 'me'"))
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    @Test
    fun version_16_machines_choose_no_cover_and_are_pulled_again() {
        KachalochkaDatabase.Schema.create(driver)
        version12MachineTable()
        exec("ALTER TABLE machine ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
        exec(
            "INSERT INTO machine(id, name, weight_mode, unit, weight_step, updated_at) " +
                "VALUES ('press', 'Жим', 'total', 'kg', 2.5, 1)",
        )
        exec("INSERT INTO syncState(user_id, lastPullAt) VALUES ('ivan', 9)")

        KachalochkaDatabase.Schema.migrate(driver, 16, 17)

        assertEquals(null, text("SELECT cover_photo FROM machine WHERE id = 'press'"))
        assertEquals(null, number("SELECT lastPullAt FROM syncState WHERE user_id = 'ivan'"))
    }

    /** Versions 5 to 12 declare the machine table this way. */
    private fun version12MachineTable() {
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
                deleted INTEGER NOT NULL DEFAULT 0,
                unit_label TEXT NOT NULL DEFAULT '',
                link_id TEXT
            )
            """.trimIndent(),
        )
    }

    /** Versions 10 to 12 declare the profile table this way. */
    private fun version12ProfileTable() {
        exec("DROP TABLE profile")
        exec(
            """
            CREATE TABLE profile (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT,
                display_name TEXT,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                friend_colors TEXT NOT NULL DEFAULT '{}',
                sex TEXT,
                birth_date TEXT,
                height_cm REAL,
                weight_unit TEXT NOT NULL DEFAULT 'kg'
            )
            """.trimIndent(),
        )
    }

    /** Versions 4 to 11 declare the set table this way. */
    private fun version11SetTable() {
        exec("DROP TABLE workout_set")
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
                deleted INTEGER NOT NULL DEFAULT 0,
                position INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    /** Version 9 declares the profile table as 8.sqm rebuilds it. */
    private fun version9ProfileTable() {
        KachalochkaDatabase.Schema.create(driver)
        exec("DROP TABLE profile")
        exec(
            """
            CREATE TABLE profile (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT,
                display_name TEXT,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                friend_colors TEXT NOT NULL DEFAULT '{}',
                sex TEXT,
                birth_date TEXT,
                height_cm REAL
            )
            """.trimIndent(),
        )
        exec("CREATE INDEX profile_updated_at_idx ON profile (updated_at)")
        exec("DROP TABLE photo")
        version11SetTable()
        version12MachineTable()
        version13VisitTable()
        exec("DROP TABLE workout_plan")
    }

    /** Versions 6 to 8 declare the profile table this way. */
    private fun version8ProfileTable() {
        KachalochkaDatabase.Schema.create(driver)
        exec("DROP TABLE profile")
        exec(
            """
            CREATE TABLE profile (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT,
                display_name TEXT,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                friend_colors TEXT NOT NULL DEFAULT '{}',
                sex TEXT,
                birth_year INTEGER,
                height_cm REAL
            )
            """.trimIndent(),
        )
        exec("CREATE INDEX profile_updated_at_idx ON profile (updated_at)")
        exec("DROP TABLE photo")
        version11SetTable()
        version12MachineTable()
        version13VisitTable()
        exec("DROP TABLE workout_plan")
    }

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

    private companion object {
        val VISIT = VisitId("0a000000-0000-4000-8000-00000000000a")
        val SET = WorkoutSetId("0b000000-0000-4000-8000-00000000000b")
        val PRESS = MachineId("0d000000-0000-4000-8000-00000000000d")
    }
}
