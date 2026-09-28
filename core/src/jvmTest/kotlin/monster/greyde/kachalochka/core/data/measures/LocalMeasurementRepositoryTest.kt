package monster.greyde.kachalochka.core.data.measures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LocalMeasurementRepositoryTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val repository = LocalMeasurementRepository(database, outbox, Dispatchers.Unconfined)
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_123)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")
    private val weight = MeasureId.random()
    private val monday = CalendarDay(2026, 9, 21)

    private fun value(
        owner: UserId?,
        day: CalendarDay = monday,
        value: Double = 82.4,
        updatedAt: Instant = now,
    ) = Measurement(MeasurementId.random(), owner, weight, day, value, updatedAt, false)

    @Test
    fun a_value_reads_back_field_for_field() =
        runTest {
            val value = value(ivan)

            repository.upsert(value)

            assertEquals(listOf(value), repository.all(ivan))
        }

    @Test
    fun an_owned_value_is_enqueued_and_an_unowned_one_is_not() =
        runTest {
            val owned = value(ivan)

            repository.upsert(value(null))
            repository.upsert(owned)

            assertEquals(
                listOf(MEASUREMENT_TABLE to owned.id.value),
                outbox.pending().map { it.tableName to it.rowId },
            )
        }

    @Test
    fun all_lists_live_values_newest_day_first_keeping_the_newest_of_one_day() =
        runTest {
            val older = value(ivan, value = 80.0)
            val newer = value(ivan, value = 81.0, updatedAt = now + 1.hours)
            val nextWeek = value(ivan, day = monday.plusDays(7))
            listOf(newer, older, nextWeek, value(ivan).copy(deleted = true), value(misha))
                .forEach { repository.upsert(it) }

            assertEquals(listOf(nextWeek, newer), repository.all(ivan))
        }

    @Test
    fun a_day_cleared_on_its_newest_row_stays_cleared_though_an_older_row_is_live() =
        runTest {
            repository.upsert(value(ivan, value = 80.0))
            val cleared = value(ivan, value = 81.0, updatedAt = now + 1.hours).copy(deleted = true)
            repository.upsert(cleared)

            assertEquals(emptyList(), repository.all(ivan))
        }

    @Test
    fun the_anonymous_owner_sees_only_unowned_values() =
        runTest {
            val anonymous = value(null)
            repository.upsert(anonymous)
            repository.upsert(value(ivan, day = monday.plusDays(1)))

            assertEquals(listOf(anonymous), repository.all(null))
        }
}
