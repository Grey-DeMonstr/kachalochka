package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalWorkoutSetRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val dispatcher: CoroutineDispatcher,
) : WorkoutSetRepository {
    private val queries = database.workoutSetQueries

    override suspend fun upsert(set: WorkoutSet) =
        withContext(dispatcher) {
            queries.transaction {
                queries.upsert(
                    set.id.value,
                    set.userId?.value,
                    set.visitId.value,
                    set.machineId.value,
                    set.weight,
                    set.reps.toLong(),
                    set.recordedAt,
                    set.updatedAt,
                    set.deleted,
                )
                if (set.userId != null) {
                    outbox.enqueue(OutboxEntry(WORKOUT_SET_TABLE, set.id.value, set.updatedAt))
                }
            }
        }

    override suspend fun forVisit(visitId: VisitId): List<WorkoutSet> =
        withContext(dispatcher) { queries.forVisit(visitId.value, ::workoutSetOf).executeAsList() }

    override suspend fun forMachine(machineId: MachineId): List<WorkoutSet> =
        withContext(dispatcher) {
            queries.forMachine(machineId.value, ::workoutSetOf).executeAsList()
        }

    override suspend fun latestPerMachine(owner: UserId?): List<WorkoutSet> =
        withContext(dispatcher) {
            queries.latestPerMachine(owner?.value, ::workoutSetOf).executeAsList()
        }
}

private fun workoutSetOf(
    id: String,
    userId: String?,
    visitId: String,
    machineId: String,
    weight: Double,
    reps: Long,
    recordedAt: Instant,
    updatedAt: Instant,
    deleted: Boolean,
) = WorkoutSet(
    id = WorkoutSetId(id),
    userId = userId?.let(::UserId),
    visitId = VisitId(visitId),
    machineId = MachineId(machineId),
    weight = weight,
    reps = reps.toInt(),
    recordedAt = recordedAt,
    updatedAt = updatedAt,
    deleted = deleted,
)
