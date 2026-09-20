package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.Machine
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
}
