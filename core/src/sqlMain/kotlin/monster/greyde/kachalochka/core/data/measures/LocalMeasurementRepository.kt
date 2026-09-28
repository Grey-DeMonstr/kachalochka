package monster.greyde.kachalochka.core.data.measures

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.MeasurementQueries
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.newestPerDay
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalMeasurementRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val dispatcher: CoroutineDispatcher,
) : MeasurementRepository {
    private val queries = database.measurementQueries

    override suspend fun upsert(measurement: Measurement) =
        withContext(dispatcher) {
            queries.transaction {
                queries.write(measurement)
                if (measurement.userId != null) {
                    outbox.enqueue(
                        OutboxEntry(
                            MEASUREMENT_TABLE,
                            measurement.id.value,
                            measurement.updatedAt,
                        ),
                    )
                }
            }
        }

    override suspend fun all(owner: UserId?): List<Measurement> =
        withContext(dispatcher) {
            newestPerDay(queries.live(owner?.value, ::measurementOf).executeAsList())
        }
}

internal fun MeasurementQueries.write(measurement: Measurement) =
    upsert(
        measurement.id.value,
        measurement.userId?.value,
        measurement.measureId.value,
        measurement.day,
        measurement.value,
        measurement.updatedAt,
        measurement.deleted,
    )

internal fun measurementOf(
    id: String,
    userId: String?,
    measureId: String,
    day: CalendarDay,
    value: Double,
    updatedAt: Instant,
    deleted: Boolean,
) = Measurement(
    id = MeasurementId(id),
    userId = userId?.let(::UserId),
    measureId = MeasureId(measureId),
    day = day,
    value = value,
    updatedAt = updatedAt,
    deleted = deleted,
)
