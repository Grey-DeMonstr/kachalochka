package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LocalVisitRepositoryTest {
    private val database = inMemoryDatabase()
    private val repository =
        LocalVisitRepository(database, OutboxDao(database), Dispatchers.Unconfined)
    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_123)

    private fun visit(
        recordedAt: Instant = t0,
        day: CalendarDay? = CalendarDay(2023, 11, 14),
        deleted: Boolean = false,
        userId: UserId? = null,
    ) = Visit(VisitId.random(), userId, day, recordedAt, recordedAt, deleted)

    @Test
    fun a_visit_reads_back_with_its_day() =
        runTest {
            val visit = visit(t0)

            repository.upsert(visit)

            assertEquals(visit, repository.byId(visit.id))
        }

    @Test
    fun an_owner_s_visits_list_newest_first_without_the_deleted() =
        runTest {
            val ivan = UserId("11111111-1111-4111-8111-111111111111")
            val older = visit(t0)
            val newer = visit(t0 + 1.days)
            val deleted = visit(t0 + 2.days, deleted = true)
            val ivans = visit(t0 + 3.days, userId = ivan)
            val undated = visit(t0 + 4.days, day = null)
            listOf(older, newer, deleted, ivans, undated).forEach { repository.upsert(it) }

            assertEquals(listOf(undated, newer, older), repository.all(null))
            assertEquals(listOf(ivans), repository.all(ivan))
        }

    @Test
    fun a_day_finds_the_owner_s_newest_live_visit_on_it() =
        runTest {
            val ivan = UserId("11111111-1111-4111-8111-111111111111")
            val fourteenth = CalendarDay(2023, 11, 14)
            val morning = visit(t0 - 3.hours, fourteenth)
            val evening = visit(t0, fourteenth)
            val deleted = visit(t0 + 1.hours, fourteenth, deleted = true)
            val ivans = visit(t0 + 2.hours, fourteenth, userId = ivan)
            val undated = visit(t0 + 3.hours, day = null)
            listOf(morning, evening, deleted, ivans, undated).forEach { repository.upsert(it) }

            assertEquals(evening, repository.onDay(null, fourteenth))
            assertEquals(ivans, repository.onDay(ivan, fourteenth))
            assertNull(repository.onDay(null, CalendarDay(2023, 11, 13)))
            assertEquals(morning, repository.byId(morning.id))
        }
}
