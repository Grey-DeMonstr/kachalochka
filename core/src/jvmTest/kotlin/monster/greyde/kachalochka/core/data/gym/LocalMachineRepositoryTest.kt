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
import kotlin.time.Instant

class LocalMachineRepositoryTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val repository = LocalMachineRepository(database, outbox, Dispatchers.Unconfined)
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_123)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")

    private val sled =
        Machine.new("Leg press", null, now).copy(
            setupNote = "Seat at 4",
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
            val tagged = sled.copy(tags = setOf("Legs", " bench  press"))

            repository.upsert(tagged)

            assertEquals(
                tagged.copy(tags = setOf("legs", "bench press")),
                repository.byId(sled.id),
            )
            assertEquals(setOf("legs", "bench press"), repository.all(null).single().tags)
            assertEquals(setOf("legs", "bench press"), repository.named(null, sled.name)?.tags)
        }

    @Test
    fun a_deleted_machine_reads_back_without_tags() =
        runTest {
            val press = Machine.new("Press", ivan, now).copy(tags = setOf("legs"))
            repository.upsert(press)

            repository.upsert(press.copy(deleted = true))

            assertEquals(emptySet(), repository.byId(press.id)?.tags)
        }

    @Test
    fun an_owned_machine_s_tags_are_rows_of_their_own_and_enqueued_with_it() =
        runTest {
            val press = Machine.new("Press", ivan, now).copy(tags = setOf("Legs"))

            repository.upsert(press)

            assertEquals(
                setOf(
                    MACHINE_TABLE to press.id.value,
                    TAG_TABLE to tagId(ivan, "legs"),
                    MACHINE_TAG_TABLE to machineTagId(press.id, "legs"),
                ),
                outbox.pending().map { it.tableName to it.rowId }.toSet(),
            )
            assertEquals(
                "legs",
                database.tagQueries
                    .byId(tagId(ivan, "legs"), ::tagOf)
                    .executeAsOne()
                    .name,
            )
        }

    @Test
    fun owners_read_their_own_tags_only() =
        runTest {
            val misha = UserId("22222222-2222-4222-8222-222222222222")
            repository.upsert(Machine.new("Press", ivan, now).copy(tags = setOf("legs")))
            repository.upsert(Machine.new("Press", misha, now).copy(tags = setOf("back")))

            assertEquals(setOf("legs"), repository.all(ivan).single().tags)
            assertEquals(setOf("back"), repository.all(misha).single().tags)
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
            val abs = Machine.new("abs", null, now)
            val gone = Machine.new("Biceps curl", null, now).copy(deleted = true)
            listOf(sled, gone, abs).forEach { repository.upsert(it) }

            assertEquals(listOf("abs", "Leg press"), repository.all(null).map { it.name })
        }

    @Test
    fun an_owned_machine_is_enqueued_and_an_unowned_one_is_not() =
        runTest {
            val owned = Machine.new("Row", UserId("9b1f0c3e-0000-4000-8000-000000000002"), now)

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
            repository.upsert(Machine.new("Leg press", ivan, now))
            repository.upsert(Machine.new("Row", misha, now))

            assertEquals(listOf("Leg press"), repository.all(ivan).map { it.name })
            assertEquals(listOf("Row"), repository.all(misha).map { it.name })
        }

    @Test
    fun a_machine_is_found_by_owner_and_name() =
        runTest {
            val ivan = UserId("11111111-1111-4111-8111-111111111111")
            val misha = UserId("22222222-2222-4222-8222-222222222222")
            val ivanPress = Machine.new("Leg press", ivan, now)
            repository.upsert(ivanPress)

            assertEquals(ivanPress.id, repository.named(ivan, "Leg press")?.id)
            assertNull(repository.named(misha, "Leg press"))
        }

    @Test
    fun a_custom_unit_reads_back_with_its_name() =
        runTest {
            val gravitron =
                Machine.new("Gravitron", null, now).copy(
                    unit = WeightUnit.Custom,
                    unitLabel = "plate",
                )

            repository.upsert(gravitron)

            assertEquals(gravitron, repository.byId(gravitron.id))
        }

    @Test
    fun a_link_of_another_owner_on_a_machine_is_not_its_tag() =
        runTest {
            val mate = UserId("33333333-3333-4333-8333-333333333333")
            val press = Machine.new("Press", ivan, now)
            repository.upsert(press)
            val theirs = Tag(tagId(mate, "arms"), mate, "arms", now, false)
            database.tagQueries.write(theirs)
            database.machineTagQueries.write(
                MachineTag(machineTagId(press.id, "arms"), mate, press.id, theirs.id, now, false),
            )

            assertEquals(emptySet(), repository.byId(press.id)?.tags)
            assertEquals(emptySet(), repository.all(ivan).single().tags)
        }
}
