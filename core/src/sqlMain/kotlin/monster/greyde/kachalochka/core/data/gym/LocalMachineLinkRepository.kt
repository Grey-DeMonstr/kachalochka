package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.MachineLinkQueries
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalMachineLinkRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val dispatcher: CoroutineDispatcher,
) : MachineLinkRepository {
    private val queries = database.machineLinkQueries

    override suspend fun upsert(link: MachineLink) =
        withContext(dispatcher) {
            queries.transaction {
                queries.write(link)
                if (link.userId != null) {
                    outbox.enqueue(OutboxEntry(MACHINE_LINK_TABLE, link.id.value, link.updatedAt))
                }
            }
        }

    override suspend fun all(owner: UserId?): List<MachineLink> =
        withContext(dispatcher) { queries.live(owner?.value, ::machineLinkOf).executeAsList() }
}

internal fun MachineLinkQueries.write(link: MachineLink) =
    upsert(
        link.id.value,
        link.userId?.value,
        link.machineId.value,
        link.linkedMachineId.value,
        link.updatedAt,
        link.deleted,
    )

internal fun machineLinkOf(
    id: String,
    userId: String?,
    machineId: String,
    linkedMachineId: String,
    updatedAt: Instant,
    deleted: Boolean,
) = MachineLink(
    id = MachineLinkId(id),
    userId = userId?.let(::UserId),
    machineId = MachineId(machineId),
    linkedMachineId = MachineId(linkedMachineId),
    updatedAt = updatedAt,
    deleted = deleted,
)
