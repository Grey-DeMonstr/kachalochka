package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.PlanQueries
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.gym.planOrder
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalPlanRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val dispatcher: CoroutineDispatcher,
) : PlanRepository {
    private val queries = database.planQueries

    override suspend fun upsert(plan: Plan) =
        withContext(dispatcher) {
            queries.transaction {
                queries.write(plan)
                if (plan.userId != null) {
                    outbox.enqueue(OutboxEntry(PLAN_TABLE, plan.id.value, plan.updatedAt))
                }
            }
        }

    override suspend fun byId(id: PlanId): Plan? =
        withContext(dispatcher) { queries.byId(id.value, ::planOf).executeAsOneOrNull() }

    override suspend fun all(owner: UserId?): List<Plan> =
        withContext(dispatcher) {
            queries.live(owner?.value, ::planOf).executeAsList().sortedWith(planOrder)
        }
}

internal fun PlanQueries.write(plan: Plan) =
    upsert(
        plan.id.value,
        plan.userId?.value,
        plan.name,
        machineIdsText(plan.machineIds),
        plan.createdAt,
        plan.updatedAt,
        plan.deleted,
    )

internal fun planOf(
    id: String,
    userId: String?,
    name: String,
    machineIds: String,
    createdAt: Instant,
    updatedAt: Instant,
    deleted: Boolean,
) = Plan(
    PlanId(id),
    userId?.let(::UserId),
    name,
    machineIdsOf(machineIds),
    createdAt,
    updatedAt,
    deleted,
)
