package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LocalVisitRepositoryTest {
    private val database = inMemoryDatabase()
    private val repository =
        LocalVisitRepository(database, OutboxDao(database), Dispatchers.Unconfined)
    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_123)

    private fun visit(
        startedAt: Instant,
        endedAt: Instant? = null,
        deleted: Boolean = false,
    ) = Visit(VisitId.random(), null, startedAt, endedAt, startedAt, deleted)

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

            assertEquals(newer, repository.active())
        }

    @Test
    fun there_is_no_active_visit_once_every_visit_ended() =
        runTest {
            repository.upsert(visit(t0, endedAt = t0 + 1.hours))

            assertNull(repository.active())
        }
}
