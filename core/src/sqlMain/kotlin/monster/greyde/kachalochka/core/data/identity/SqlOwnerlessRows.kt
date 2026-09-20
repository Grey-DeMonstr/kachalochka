package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Clock
import kotlin.time.Instant

class SqlOwnerlessRows(
    private val database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
) : OwnerlessRows {
    override suspend fun claim(owner: UserId) =
        withContext(dispatcher) {
            val now = clock.now()
            database.transaction {
                val visitIds = database.visitQueries.ownerlessIds().executeAsList()
                val machineIds = database.machineQueries.ownerlessIds().executeAsList()
                val setIds = database.workoutSetQueries.ownerlessIds().executeAsList()

                database.visitQueries.claimOwnerless(owner.value, now)
                database.machineQueries.claimOwnerless(owner.value, now)
                database.workoutSetQueries.claimOwnerless(owner.value, now)

                enqueue(VISIT_TABLE, visitIds, now)
                enqueue(MACHINE_TABLE, machineIds, now)
                enqueue(WORKOUT_SET_TABLE, setIds, now)
            }
        }

    private fun enqueue(
        table: String,
        ownerlessIds: List<String>,
        now: Instant,
    ) {
        ownerlessIds.forEach { outbox.enqueue(OutboxEntry(table, it, now)) }
    }
}
