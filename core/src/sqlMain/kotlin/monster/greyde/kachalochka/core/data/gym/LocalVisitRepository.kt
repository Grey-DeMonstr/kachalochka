package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalVisitRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val dispatcher: CoroutineDispatcher,
) : VisitRepository {
    private val queries = database.visitQueries

    override suspend fun upsert(visit: Visit) =
        withContext(dispatcher) {
            queries.transaction {
                queries.upsert(
                    visit.id.value,
                    visit.userId?.value,
                    visit.day,
                    visit.recordedAt,
                    visit.updatedAt,
                    visit.deleted,
                )
                if (visit.userId != null) {
                    outbox.enqueue(OutboxEntry(VISIT_TABLE, visit.id.value, visit.updatedAt))
                }
            }
        }

    override suspend fun byId(id: VisitId): Visit? =
        withContext(dispatcher) { queries.byId(id.value, ::visitOf).executeAsOneOrNull() }

    override suspend fun onDay(
        owner: UserId?,
        day: CalendarDay,
    ): Visit? =
        withContext(dispatcher) {
            queries.onDay(day = day, owner = owner?.value, mapper = ::visitOf).executeAsOneOrNull()
        }

    override suspend fun all(owner: UserId?): List<Visit> =
        withContext(dispatcher) {
            queries.forOwner(owner?.value, ::visitOf).executeAsList()
        }
}

internal fun visitOf(
    id: String,
    userId: String?,
    day: CalendarDay?,
    recordedAt: Instant,
    updatedAt: Instant,
    deleted: Boolean,
) = Visit(VisitId(id), userId?.let(::UserId), day, recordedAt, updatedAt, deleted)
