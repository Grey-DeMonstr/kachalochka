package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.LocalSyncRows
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class VisitNormalizerTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val visits = LocalVisitRepository(database, outbox, Dispatchers.Unconfined)
    private val sets = LocalWorkoutSetRepository(database, outbox, Dispatchers.Unconfined)
    private val pulled = LocalSyncRows(database)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val later = t0 + 1.hours
    private val clock =
        object : Clock {
            override fun now(): Instant = later
        }
    private val normalizer = VisitNormalizer(visits, sets, clock) { Duration.ZERO }
    private val fourteenth = CalendarDay(2023, 11, 14)

    private fun visit(
        recordedAt: Instant,
        day: CalendarDay? = fourteenth,
        owner: UserId = ivan,
    ) = Visit(VisitId.random(), owner, day, recordedAt, t0, false)

    private fun set(visit: Visit) =
        WorkoutSet(
            WorkoutSetId.random(),
            visit.userId,
            visit.id,
            MachineId.random(),
            70.0,
            10,
            0,
            visit.recordedAt,
            t0,
            false,
        )

    @Test
    fun the_newest_visit_of_a_day_stays_and_the_others_leave_with_their_sets() =
        runTest {
            val morning = visit(t0 - 3.hours)
            val evening = visit(t0)
            val morningSet = set(morning)
            pulled.writeVisit(morning)
            pulled.writeVisit(evening)
            pulled.writeSet(morningSet)
            pulled.writeSet(set(evening))

            assertTrue(normalizer.normalize(ivan))

            assertEquals(true, visits.byId(morning.id)?.deleted)
            assertEquals(emptyList(), sets.forVisit(morning.id))
            assertEquals(evening, visits.onDay(ivan, fourteenth))
            assertEquals(
                setOf(morning.id.value, morningSet.id.value),
                outbox.pending().map { it.rowId }.toSet(),
            )
        }

    @Test
    fun a_visit_opened_after_the_day_s_workout_and_left_empty_is_the_one_that_goes() =
        runTest {
            val workout = visit(t0 - 3.hours)
            val empty = visit(t0)
            val workoutSet = set(workout)
            pulled.writeVisit(workout)
            pulled.writeVisit(empty)
            pulled.writeSet(workoutSet)

            assertTrue(normalizer.normalize(ivan))

            assertEquals(true, visits.byId(empty.id)?.deleted)
            assertEquals(listOf(workoutSet), sets.forVisit(workout.id))
            assertEquals(workout, visits.onDay(ivan, fourteenth))
        }

    @Test
    fun a_second_run_writes_nothing() =
        runTest {
            pulled.writeVisit(visit(t0 - 3.hours))
            pulled.writeVisit(visit(t0))
            pulled.writeVisit(visit(t0 - 1.days, day = null))
            normalizer.normalize(ivan)
            val pending = outbox.pending()

            assertFalse(normalizer.normalize(ivan))
            assertEquals(pending, outbox.pending())
        }

    @Test
    fun a_visit_an_old_client_pushed_without_a_day_is_given_one_and_sent_back() =
        runTest {
            val old = visit(t0, day = null)
            pulled.writeVisit(old)

            assertTrue(normalizer.normalize(ivan))

            assertEquals(old.copy(day = fourteenth, updatedAt = later), visits.byId(old.id))
            assertEquals(listOf(old.id.value), outbox.pending().map { it.rowId })
        }

    @Test
    fun another_owner_s_visits_are_left_alone() =
        runTest {
            val theirs = visit(t0 - 3.hours, owner = misha)
            pulled.writeVisit(theirs)
            pulled.writeVisit(visit(t0, owner = misha))

            assertFalse(normalizer.normalize(ivan))
            assertEquals(theirs, visits.byId(theirs.id))
        }
}
