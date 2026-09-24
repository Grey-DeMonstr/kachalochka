package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
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
        endedAt: Instant? = null,
        deleted: Boolean = false,
        userId: UserId? = null,
    ) = Visit(VisitId.random(), userId, recordedAt, endedAt, recordedAt, deleted)

    @Test
    fun a_visit_reads_back_with_its_end() =
        runTest {
            val ended = visit(t0, endedAt = t0 + 1.hours)

            repository.upsert(ended)

            assertEquals(ended, repository.byId(ended.id))
        }

    @Test
    fun the_active_visit_is_the_newest_one_without_an_end() =
        runTest {
            val older = visit(t0)
            val newer = visit(t0 + 1.hours)
            val ended = visit(t0 + 2.hours, endedAt = t0 + 3.hours)
            val deleted = visit(t0 + 4.hours, deleted = true)
            listOf(older, newer, ended, deleted).forEach { repository.upsert(it) }

            assertEquals(newer, repository.active(null))
        }

    @Test
    fun there_is_no_active_visit_once_every_visit_ended() =
        runTest {
            repository.upsert(visit(t0, endedAt = t0 + 1.hours))

            assertNull(repository.active(null))
        }

    @Test
    fun each_account_resolves_its_own_active_visit() =
        runTest {
            val ivan = UserId("11111111-1111-4111-8111-111111111111")
            val misha = UserId("22222222-2222-4222-8222-222222222222")
            val ivanVisit = visit(userId = ivan)
            val mishaVisit = visit(userId = misha)
            repository.upsert(ivanVisit)
            repository.upsert(mishaVisit)

            assertEquals(ivanVisit.id, repository.active(ivan)?.id)
            assertEquals(mishaVisit.id, repository.active(misha)?.id)
        }

    @Test
    fun an_owned_visit_is_not_the_anonymous_active_visit() =
        runTest {
            val ivan = UserId("11111111-1111-4111-8111-111111111111")
            repository.upsert(visit(userId = ivan))

            assertNull(repository.active(null))
        }

    @Test
    fun an_owner_s_visits_list_newest_first_without_the_deleted() =
        runTest {
            val ivan = UserId("11111111-1111-4111-8111-111111111111")
            val older = visit(t0, endedAt = t0 + 1.hours)
            val newer = visit(t0 + 1.days)
            val deleted = visit(t0 + 2.days, deleted = true)
            val ivans = visit(t0 + 3.days, userId = ivan)
            listOf(older, newer, deleted, ivans).forEach { repository.upsert(it) }

            assertEquals(listOf(newer, older), repository.all(null))
            assertEquals(listOf(ivans), repository.all(ivan))
        }
}
