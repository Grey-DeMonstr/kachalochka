package monster.greyde.kachalochka.core.data.measures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LocalMeasureRepositoryTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val repository = LocalMeasureRepository(database, outbox, Dispatchers.Unconfined)
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_123)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")

    private fun measure(
        owner: UserId?,
        name: String = "Предплечье",
        kind: MeasureKind? = null,
        position: Int = 0,
    ) = Measure(MeasureId.random(), owner, name, "см", kind, position, now, false)

    @Test
    fun a_measure_reads_back_field_for_field() =
        runTest {
            val measure = measure(ivan, kind = MeasureKind.BodyFat, position = 7)

            repository.upsert(measure)

            assertEquals(listOf(measure), repository.all(ivan))
        }

    @Test
    fun an_owned_measure_is_enqueued_and_an_unowned_one_is_not() =
        runTest {
            val owned = measure(ivan)

            repository.upsert(measure(null))
            repository.upsert(owned)

            assertEquals(
                listOf(MEASURE_TABLE to owned.id.value),
                outbox.pending().map { it.tableName to it.rowId },
            )
        }

    @Test
    fun all_lists_the_owner_s_live_measures_by_position_then_name() =
        runTest {
            val third = measure(ivan, name = "Шея", position = 1)
            val second = measure(ivan, name = "Бедро", position = 1)
            val first = measure(ivan, name = "Шея", position = 0)
            listOf(third, measure(ivan).copy(deleted = true), second, measure(misha), first)
                .forEach { repository.upsert(it) }

            assertEquals(listOf(first, second, third), repository.all(ivan))
        }

    @Test
    fun the_anonymous_owner_sees_only_unowned_measures() =
        runTest {
            val anonymous = measure(null)
            repository.upsert(anonymous)
            repository.upsert(measure(ivan))

            assertEquals(listOf(anonymous), repository.all(null))
        }

    @Test
    fun kinds_counts_deleted_measures_and_only_the_owner_s() =
        runTest {
            repository.upsert(measure(ivan, kind = MeasureKind.Weight))
            repository.upsert(measure(ivan, kind = MeasureKind.Neck).copy(deleted = true))
            repository.upsert(measure(ivan))
            repository.upsert(measure(misha, kind = MeasureKind.Hips))

            assertEquals(setOf(MeasureKind.Weight, MeasureKind.Neck), repository.kinds(ivan))
            assertEquals(emptySet(), repository.kinds(null))
        }
}
