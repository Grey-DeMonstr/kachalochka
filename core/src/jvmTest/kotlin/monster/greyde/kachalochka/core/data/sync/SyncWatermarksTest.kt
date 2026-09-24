package monster.greyde.kachalochka.core.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.db.kachalochkaDatabase
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SyncWatermarksTest {
    private val watermarks = SyncWatermarks(inMemoryDatabase())
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun an_account_that_never_pulled_has_no_watermark() {
        assertNull(watermarks.lastPullAt(ivan))
    }

    @Test
    fun each_account_advances_its_own_watermark() {
        watermarks.advance(ivan, t0)
        watermarks.advance(misha, t0 + 2.hours)

        assertEquals(t0, watermarks.lastPullAt(ivan))
        assertEquals(t0 + 2.hours, watermarks.lastPullAt(misha))
    }

    @Test
    fun advancing_replaces_the_previous_watermark() {
        watermarks.advance(ivan, t0)
        watermarks.advance(ivan, t0 + 1.hours)

        assertEquals(t0 + 1.hours, watermarks.lastPullAt(ivan))
    }

    @Test
    fun a_database_from_the_first_release_migrates_to_per_account_watermarks() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KachalochkaDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE syncState", 0)
        driver.execute(
            null,
            "CREATE TABLE syncState (id INTEGER NOT NULL PRIMARY KEY, lastPullAt INTEGER)",
            0,
        )
        driver.execute(null, "INSERT INTO syncState(id, lastPullAt) VALUES (0, NULL)", 0)

        KachalochkaDatabase.Schema.migrate(driver, 1, 2)
        val migrated = SyncWatermarks(kachalochkaDatabase(driver))
        migrated.advance(ivan, t0)

        assertEquals(2L, KachalochkaDatabase.Schema.version)
        assertEquals(t0, migrated.lastPullAt(ivan))
    }
}
