package monster.greyde.kachalochka.core.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.kachalochkaDatabase
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class OutboxDaoTest {
    private fun dao(): OutboxDao {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KachalochkaDatabase.Schema.create(driver)
        return OutboxDao(kachalochkaDatabase(driver))
    }

    private fun entry(
        rowId: String,
        secondsFromEpoch: Long,
    ) = OutboxEntry("visit", rowId, Instant.fromEpochSeconds(secondsFromEpoch))

    @Test
    fun an_enqueued_entry_is_pending() {
        val dao = dao()
        val entry = entry("row-1", 100)

        dao.enqueue(entry)

        assertEquals(listOf(entry), dao.pending())
    }

    @Test
    fun enqueueing_the_same_row_twice_keeps_one_entry() {
        val dao = dao()
        dao.enqueue(entry("row-1", 100))
        dao.enqueue(entry("row-1", 200))

        assertEquals(listOf(entry("row-1", 200)), dao.pending())
    }

    @Test
    fun pending_entries_are_ordered_by_enqueue_time() {
        val dao = dao()
        dao.enqueue(entry("row-late", 300))
        dao.enqueue(entry("row-early", 100))

        assertEquals(listOf("row-early", "row-late"), dao.pending().map { it.rowId })
    }

    @Test
    fun a_sub_second_enqueue_time_survives_the_round_trip() {
        val dao = dao()
        val entry = OutboxEntry("visit", "row-1", Instant.fromEpochMilliseconds(1_700_000_000_123))

        dao.enqueue(entry)

        assertEquals(listOf(entry), dao.pending())
    }

    @Test
    fun a_removed_entry_is_no_longer_pending() {
        val dao = dao()
        dao.enqueue(entry("row-1", 100))

        dao.remove("visit", "row-1")

        assertTrue(dao.pending().isEmpty())
    }

    @Test
    fun a_pushed_entry_enqueued_again_meanwhile_stays_pending() {
        val dao = dao()
        val pushed = entry("row-1", 100)
        dao.enqueue(pushed)
        dao.enqueue(entry("row-1", 200))

        dao.removePushed(pushed)

        assertEquals(listOf(entry("row-1", 200)), dao.pending())
    }
}
