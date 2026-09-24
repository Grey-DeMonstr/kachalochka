package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalMachineRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val dispatcher: CoroutineDispatcher,
) : MachineRepository {
    private val queries = database.machineQueries

    override suspend fun upsert(machine: Machine) =
        withContext(dispatcher) {
            queries.transaction {
                queries.upsert(
                    machine.id.value,
                    machine.userId?.value,
                    machine.name,
                    machine.setupNote,
                    machine.weightMode.wireName(),
                    machine.platformWeight,
                    machine.platformIncluded,
                    machine.unit.wireName(),
                    machine.weightStep,
                    machine.updatedAt,
                    machine.deleted,
                )
                if (machine.userId != null) {
                    outbox.enqueue(OutboxEntry(MACHINE_TABLE, machine.id.value, machine.updatedAt))
                }
            }
        }

    override suspend fun byId(id: MachineId): Machine? =
        withContext(dispatcher) { queries.byId(id.value, ::machineOf).executeAsOneOrNull() }

    // SQLite folds case for ASCII only, so Cyrillic names are ordered here.
    override suspend fun all(owner: UserId?): List<Machine> =
        withContext(dispatcher) {
            queries
                .live(owner?.value, ::machineOf)
                .executeAsList()
                .sortedBy { it.name.lowercase() }
        }

    override suspend fun named(
        owner: UserId?,
        name: String,
    ): Machine? =
        withContext(dispatcher) {
            queries.named(name, owner?.value, ::machineOf).executeAsOneOrNull()
        }
}

internal fun machineOf(
    id: String,
    userId: String?,
    name: String,
    setupNote: String,
    weightMode: String,
    platformWeight: Double,
    platformIncluded: Boolean,
    unit: String,
    weightStep: Double,
    updatedAt: Instant,
    deleted: Boolean,
) = Machine(
    id = MachineId(id),
    userId = userId?.let(::UserId),
    name = name,
    setupNote = setupNote,
    weightMode = weightModeOf(weightMode),
    platformWeight = platformWeight,
    platformIncluded = platformIncluded,
    unit = weightUnitOf(unit),
    weightStep = weightStep,
    updatedAt = updatedAt,
    deleted = deleted,
)
