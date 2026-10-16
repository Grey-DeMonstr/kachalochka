package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.MachineQueries
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalMachineRepository(
    private val database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val dispatcher: CoroutineDispatcher,
) : MachineRepository {
    private val queries = database.machineQueries

    override suspend fun upsert(machine: Machine) =
        withContext(dispatcher) {
            database.transaction {
                queries.write(machine)
                if (machine.userId != null) {
                    outbox.enqueue(OutboxEntry(MACHINE_TABLE, machine.id.value, machine.updatedAt))
                }
                database.writeTagsOf(machine)
            }
        }

    override suspend fun byId(id: MachineId): Machine? =
        withContext(dispatcher) {
            database.transactionWithResult {
                queries.byId(id.value, ::machineOf).executeAsOneOrNull()?.let { machine ->
                    machine.copy(tags = database.machineTags(listOf(id))[id].orEmpty())
                }
            }
        }

    // SQLite folds case for ASCII only, so Cyrillic names are ordered here.
    override suspend fun all(owner: UserId?): List<Machine> =
        withContext(dispatcher) {
            database.transactionWithResult {
                val tags = database.ownerMachineTags(owner)
                queries
                    .live(owner?.value, ::machineOf)
                    .executeAsList()
                    .map { it.copy(tags = tags[it.id].orEmpty()) }
                    .sortedBy { it.name.lowercase() }
            }
        }

    override suspend fun named(
        owner: UserId?,
        name: String,
    ): Machine? =
        withContext(dispatcher) {
            database.transactionWithResult {
                queries.named(name, owner?.value, ::machineOf).executeAsOneOrNull()?.let {
                    it.copy(tags = database.machineTags(listOf(it.id))[it.id].orEmpty())
                }
            }
        }
}

internal fun MachineQueries.write(machine: Machine) =
    upsert(
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
        machine.unitLabel,
        machine.coverPhoto?.value,
    )

/** Without its tags, which live in `machine_tag`; the legacy columns are taken and dropped. */
@Suppress("UNUSED_PARAMETER")
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
    unitLabel: String,
    linkId: String?,
    tags: String,
    coverPhoto: String?,
) = Machine(
    id = MachineId(id),
    userId = userId?.let(::UserId),
    name = name,
    setupNote = setupNote,
    weightMode = weightModeOf(weightMode),
    platformWeight = platformWeight,
    platformIncluded = platformIncluded,
    unit = weightUnitOf(unit),
    unitLabel = unitLabel,
    weightStep = weightStep,
    updatedAt = updatedAt,
    deleted = deleted,
    coverPhoto = photoIdOf(coverPhoto),
)
