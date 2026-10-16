package monster.greyde.kachalochka.core.data.gym

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.kachalochkaDatabase
import monster.greyde.kachalochka.core.data.sync.SyncHarness
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TagConversionTest {
    private val driver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(KachalochkaDatabase.Schema::create)
    private val h = SyncHarness(kachalochkaDatabase(driver))
    private val database = h.database
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val now = Instant.parse("2026-10-16T10:00:00Z")

    private suspend fun stored(
        machine: Machine,
        mirror: String,
    ): Machine {
        h.machines.upsert(machine)
        withMirror(machine.id.value, mirror)
        return machine
    }

    private fun withMirror(
        id: String,
        mirror: String,
    ) {
        driver.execute(null, "UPDATE machine SET tags = '$mirror' WHERE id = '$id'", 0)
    }

    private fun pending() =
        h.outbox
            .pending()
            .map { it.tableName to it.rowId }
            .toSet()

    @Test
    fun a_machine_still_waiting_to_be_pushed_takes_its_tags_from_its_column_once() =
        runTest {
            val press = stored(Machine.new("Жим", ivan, now), """["Ноги", "НОГИ", "Спина"]""")

            database.convertTagMirrors(now)
            val first = h.outbox.pending()
            database.convertTagMirrors(now + 1.minutes)

            assertEquals(setOf("ноги", "спина"), h.machines.byId(press.id)?.tags)
            assertEquals(
                setOf(
                    MACHINE_TABLE to press.id.value,
                    TAG_TABLE to tagId(ivan, "ноги"),
                    TAG_TABLE to tagId(ivan, "спина"),
                    MACHINE_TAG_TABLE to machineTagId(press.id, "ноги"),
                    MACHINE_TAG_TABLE to machineTagId(press.id, "спина"),
                ),
                pending(),
            )
            assertEquals(first, h.outbox.pending())
        }

    @Test
    fun a_pushed_machine_is_not_converted_and_takes_the_server_s_tags_on_the_next_pull() =
        runTest {
            val press = Machine.new("Жим", ivan, now)
            h.machines.upsert(press)
            h.pass.run(listOf(ivan))
            withMirror(press.id.value, """["ноги", "спина"]""")

            database.convertTagMirrors(now)

            assertEquals(emptySet(), h.machines.byId(press.id)?.tags)
            assertEquals(emptySet(), pending())

            val legs = Tag(tagId(ivan, "ноги"), ivan, "ноги", now, false)
            val back = Tag(tagId(ivan, "спина"), ivan, "спина", now + 1.minutes, true)
            h.gateway.tagsToPull = listOf(legs, back)
            h.gateway.machineTagsToPull =
                listOf(
                    MachineTag(
                        machineTagId(press.id, "ноги"),
                        ivan,
                        press.id,
                        legs.id,
                        now,
                        false,
                    ),
                    MachineTag(
                        machineTagId(press.id, "спина"),
                        ivan,
                        press.id,
                        back.id,
                        now,
                        true,
                    ),
                )
            h.pass.run(listOf(ivan))

            assertEquals(setOf("ноги"), h.machines.byId(press.id)?.tags)
        }

    @Test
    fun a_machine_that_has_ever_had_a_tag_row_is_left_alone() =
        runTest {
            val press = Machine.new("Жим", ivan, now).copy(tags = setOf("ноги"))
            h.machines.upsert(press)
            h.machines.upsert(press.copy(tags = emptySet(), updatedAt = now + 1.minutes))
            withMirror(press.id.value, """["Спина"]""")

            database.convertTagMirrors(now + 2.minutes)

            assertEquals(emptySet(), h.machines.byId(press.id)?.tags)
        }

    @Test
    fun unreadable_text_converts_to_no_tags() =
        runTest {
            val press = stored(Machine.new("Жим", ivan, now), "{")

            database.convertTagMirrors(now)

            assertEquals(emptySet(), h.machines.byId(press.id)?.tags)
        }

    @Test
    fun a_machine_that_cannot_be_converted_is_skipped_and_the_others_are_converted() =
        runTest {
            driver.execute(
                null,
                "INSERT INTO machine(id, name, weight_mode, unit, weight_step, updated_at, " +
                    "tags) VALUES ('not-an-id', 'Тяга', 'total', 'kg', 2.5, 1, '[\"спина\"]')",
                0,
            )
            val press = stored(Machine.new("Жим", null, now), """["Ноги"]""")

            database.convertTagMirrors(now)

            assertEquals(setOf("ноги"), h.machines.byId(press.id)?.tags)
        }

    @Test
    fun an_anonymous_machine_s_rows_wait_for_the_claim() =
        runTest {
            val press = stored(Machine.new("Жим", null, now), """["Ноги"]""")

            database.convertTagMirrors(now)

            assertEquals(setOf("ноги"), h.machines.byId(press.id)?.tags)
            assertEquals(emptyList(), h.outbox.pending())
        }

    @Test
    fun unreadable_entries_of_the_column_read_as_no_tags() {
        assertEquals(emptySet(), tagsOf("{"))
        assertEquals(setOf("Ноги"), tagsOf("""["Ноги", 3, " Ноги "]"""))
    }
}
