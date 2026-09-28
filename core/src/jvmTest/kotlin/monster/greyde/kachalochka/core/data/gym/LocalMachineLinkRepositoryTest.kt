package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LocalMachineLinkRepositoryTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val repository = LocalMachineLinkRepository(database, outbox, Dispatchers.Unconfined)
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_123)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")

    private fun link(owner: UserId?) =
        MachineLink(
            MachineLinkId.random(),
            owner,
            MachineId.random(),
            MachineId.random(),
            now,
            false,
        )

    @Test
    fun a_link_reads_back_field_for_field() =
        runTest {
            val link = link(ivan)

            repository.upsert(link)

            assertEquals(listOf(link), repository.all(ivan))
        }

    @Test
    fun an_owned_link_is_enqueued_and_an_unowned_one_is_not() =
        runTest {
            val owned = link(ivan)

            repository.upsert(link(null))
            repository.upsert(owned)

            assertEquals(
                listOf(MACHINE_LINK_TABLE to owned.id.value),
                outbox.pending().map { it.tableName to it.rowId },
            )
        }

    @Test
    fun all_leaves_out_deleted_links_and_other_owners_links() =
        runTest {
            val live = link(ivan)
            listOf(live, link(ivan).copy(deleted = true), link(misha), link(null))
                .forEach { repository.upsert(it) }

            assertEquals(listOf(live), repository.all(ivan))
        }

    @Test
    fun the_anonymous_owner_sees_only_unowned_links() =
        runTest {
            val anonymous = link(null)
            repository.upsert(anonymous)
            repository.upsert(link(ivan))

            assertEquals(listOf(anonymous), repository.all(null))
        }
}
