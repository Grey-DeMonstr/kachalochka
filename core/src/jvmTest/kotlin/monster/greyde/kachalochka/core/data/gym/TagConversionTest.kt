package monster.greyde.kachalochka.core.data.gym

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.kachalochkaDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TagConversionTest {
    private val driver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(KachalochkaDatabase.Schema::create)
    private val database = kachalochkaDatabase(driver)
    private val outbox = OutboxDao(database)
    private val machines = LocalMachineRepository(database, outbox, Dispatchers.Unconfined)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val now = Instant.parse("2026-10-16T10:00:00Z")

    private suspend fun stored(
        machine: Machine,
        mirror: String,
    ): Machine {
        machines.upsert(machine)
        driver.execute(
            null,
            "UPDATE machine SET tags = '$mirror' WHERE id = '${machine.id.value}'",
            0,
        )
        return machine
    }

    @Test
    fun a_machine_takes_its_tags_from_its_column_once() =
        runTest {
            val press = stored(Machine.new("Жим", ivan, now), """["Ноги", "НОГИ", "Спина"]""")
            outbox.pending().forEach { outbox.removePushed(it) }

            database.convertTagMirrors(now)
            val first = outbox.pending()
            database.convertTagMirrors(now + 1.minutes)

            assertEquals(setOf("ноги", "спина"), machines.byId(press.id)?.tags)
            assertEquals(4, first.size)
            assertEquals(first, outbox.pending())
        }

    @Test
    fun a_machine_that_has_ever_had_a_tag_row_is_left_alone() =
        runTest {
            val press = Machine.new("Жим", ivan, now).copy(tags = setOf("ноги"))
            machines.upsert(press)
            machines.upsert(press.copy(tags = emptySet(), updatedAt = now + 1.minutes))
            stored(press.copy(tags = emptySet(), updatedAt = now + 1.minutes), """["Спина"]""")

            database.convertTagMirrors(now + 2.minutes)

            assertEquals(emptySet(), machines.byId(press.id)?.tags)
        }

    @Test
    fun unreadable_text_converts_to_no_tags() =
        runTest {
            val press = stored(Machine.new("Жим", ivan, now), "{")

            database.convertTagMirrors(now)

            assertEquals(emptySet(), machines.byId(press.id)?.tags)
        }

    @Test
    fun an_anonymous_machine_s_rows_wait_for_the_claim() =
        runTest {
            val press = stored(Machine.new("Жим", null, now), """["Ноги"]""")

            database.convertTagMirrors(now)

            assertEquals(setOf("ноги"), machines.byId(press.id)?.tags)
            assertEquals(emptyList(), outbox.pending())
        }
}
