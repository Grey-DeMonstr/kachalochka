package monster.greyde.kachalochka.core.data.measures

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.MeasureQueries
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.measureKindOf
import monster.greyde.kachalochka.core.domain.measures.measureOrder
import monster.greyde.kachalochka.core.domain.measures.wireName
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalMeasureRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val dispatcher: CoroutineDispatcher,
) : MeasureRepository {
    private val queries = database.measureQueries

    override suspend fun upsert(measure: Measure) =
        withContext(dispatcher) {
            queries.transaction {
                queries.write(measure)
                if (measure.userId != null) {
                    outbox.enqueue(OutboxEntry(MEASURE_TABLE, measure.id.value, measure.updatedAt))
                }
            }
        }

    // One order on both platforms, whatever each database's collation.
    override suspend fun all(owner: UserId?): List<Measure> =
        withContext(dispatcher) {
            queries.live(owner?.value, ::measureOf).executeAsList().sortedWith(measureOrder)
        }

    override suspend fun predefined(owner: UserId?): List<Measure> =
        withContext(dispatcher) {
            queries
                .predefined(
                    owner?.value,
                    ::measureOf,
                ).executeAsList()
                .filter { it.kind != null }
        }
}

internal fun MeasureQueries.write(measure: Measure) =
    upsert(
        measure.id.value,
        measure.userId?.value,
        measure.name,
        measure.unit,
        measure.kind?.wireName(),
        measure.position.toLong(),
        measure.updatedAt,
        measure.deleted,
    )

internal fun measureOf(
    id: String,
    userId: String?,
    name: String,
    unit: String,
    kind: String?,
    position: Long,
    updatedAt: Instant,
    deleted: Boolean,
) = Measure(
    id = MeasureId(id),
    userId = userId?.let(::UserId),
    name = name,
    unit = unit,
    kind = kind?.let(::measureKindOf),
    position = position.toInt(),
    updatedAt = updatedAt,
    deleted = deleted,
)
