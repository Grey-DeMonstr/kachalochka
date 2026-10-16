package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LocalMachineRepositoryTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val repository = LocalMachineRepository(database, outbox, Dispatchers.Unconfined)
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_123)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")

    private val sled =
        Machine.new("Жим ногами", null, now).copy(
            setupNote = "Сиденье на 4",
            weightMode = WeightMode.PerSide,
            platformWeight = 20.0,
            platformIncluded = true,
            unit = WeightUnit.Lb,
            weightStep = 5.0,
        )

    @Test
    fun a_machine_reads_back_field_for_field() =
        runTest {
            repository.upsert(sled)

            assertEquals(sled, repository.byId(sled.id))
        }

    @Test
    fun a_machine_keeps_its_tags_as_keys() =
        runTest {
            val tagged = sled.copy(tags = setOf("Ноги", " жим  лёжа"))

            repository.upsert(tagged)

            assertEquals(tagged.copy(tags = setOf("ноги", "жим лёжа")), repository.byId(sled.id))
            assertEquals(setOf("ноги", "жим лёжа"), repository.all(null).single().tags)
            assertEquals(setOf("ноги", "жим лёжа"), repository.named(null, sled.name)?.tags)
        }

    @Test
    fun an_owned_machine_s_tags_are_rows_of_their_own_and_enqueued_with_it() =
        runTest {
            val press = Machine.new("Жим", ivan, now).copy(tags = setOf("Ноги"))

            repository.upsert(press)

            assertEquals(
                setOf(
                    MACHINE_TABLE to press.id.value,
                    TAG_TABLE to tagId(ivan, "ноги"),
                    MACHINE_TAG_TABLE to machineTagId(press.id, "ноги"),
                ),
                outbox.pending().map { it.tableName to it.rowId }.toSet(),
            )
            assertEquals(
                "ноги",
                database.tagQueries
                    .byId(tagId(ivan, "ноги"), ::tagOf)
                    .executeAsOne()
                    .name,
            )
        }

    @Test
    fun a_tag_no_live_machine_holds_any_more_is_deleted_and_one_still_held_stays() =
        runTest {
            val press = Machine.new("Жим", ivan, now).copy(tags = setOf("ноги", "спина"))
            val row = Machine.new("Тяга", ivan, now).copy(tags = setOf("спина"))
            repository.upsert(press)
            repository.upsert(row)

            repository.upsert(press.copy(tags = emptySet(), updatedAt = now + 1.minutes))

            fun tag(name: String) =
                database.tagQueries.byId(tagId(ivan, name), ::tagOf).executeAsOne()
            assertEquals(true, tag("ноги").deleted)
            assertEquals(false, tag("спина").deleted)
            assertEquals(emptySet(), repository.byId(press.id)?.tags)
            assertEquals(setOf("спина"), repository.byId(row.id)?.tags)
        }

    @Test
    fun a_deleted_machine_loses_its_tags_and_their_rows() =
        runTest {
            val press = Machine.new("Жим", ivan, now).copy(tags = setOf("ноги"))
            repository.upsert(press)

            repository.upsert(press.copy(deleted = true, updatedAt = now + 1.minutes))

            assertEquals(emptySet(), repository.byId(press.id)?.tags)
            assertEquals(
                true,
                database.tagQueries
                    .byId(tagId(ivan, "ноги"), ::tagOf)
                    .executeAsOne()
                    .deleted,
            )
        }

    @Test
    fun owners_read_their_own_tags_only() =
        runTest {
            val misha = UserId("22222222-2222-4222-8222-222222222222")
            repository.upsert(Machine.new("Жим", ivan, now).copy(tags = setOf("ноги")))
            repository.upsert(Machine.new("Жим", misha, now).copy(tags = setOf("спина")))

            assertEquals(setOf("ноги"), repository.all(ivan).single().tags)
            assertEquals(setOf("спина"), repository.all(misha).single().tags)
        }

    @Test
    fun a_machine_keeps_its_chosen_cover() =
        runTest {
            val covered = sled.copy(coverPhoto = PhotoId.random())

            repository.upsert(covered)

            assertEquals(covered, repository.byId(sled.id))
        }

    @Test
    fun all_leaves_out_deleted_machines_and_sorts_by_name() =
        runTest {
            val abs = Machine.new("аб", null, now)
            val gone = Machine.new("Бицепс", null, now).copy(deleted = true)
            listOf(sled, gone, abs).forEach { repository.upsert(it) }

            assertEquals(listOf("аб", "Жим ногами"), repository.all(null).map { it.name })
        }

    @Test
    fun an_owned_machine_is_enqueued_and_an_unowned_one_is_not() =
        runTest {
            val owned = Machine.new("Тяга", UserId("9b1f0c3e-0000-4000-8000-000000000002"), now)

            repository.upsert(sled)
            repository.upsert(owned)

            assertEquals(
                listOf(MACHINE_TABLE to owned.id.value),
                outbox.pending().map { it.tableName to it.rowId },
            )
        }

    @Test
    fun machines_are_listed_for_their_owner_only() =
        runTest {
            val ivan = UserId("11111111-1111-4111-8111-111111111111")
            val misha = UserId("22222222-2222-4222-8222-222222222222")
            repository.upsert(Machine.new("Жим ногами", ivan, now))
            repository.upsert(Machine.new("Тяга", misha, now))

            assertEquals(listOf("Жим ногами"), repository.all(ivan).map { it.name })
            assertEquals(listOf("Тяга"), repository.all(misha).map { it.name })
        }

    @Test
    fun a_machine_is_found_by_owner_and_name() =
        runTest {
            val ivan = UserId("11111111-1111-4111-8111-111111111111")
            val misha = UserId("22222222-2222-4222-8222-222222222222")
            val ivanPress = Machine.new("Жим ногами", ivan, now)
            repository.upsert(ivanPress)

            assertEquals(ivanPress.id, repository.named(ivan, "Жим ногами")?.id)
            assertNull(repository.named(misha, "Жим ногами"))
        }

    @Test
    fun a_custom_unit_reads_back_with_its_name() =
        runTest {
            val gravitron =
                Machine.new("Гравитрон", null, now).copy(
                    unit = WeightUnit.Custom,
                    unitLabel = "плитка",
                )

            repository.upsert(gravitron)

            assertEquals(gravitron, repository.byId(gravitron.id))
        }
}
