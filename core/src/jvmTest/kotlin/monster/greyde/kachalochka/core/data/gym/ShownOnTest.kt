package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.ShownVisit
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.allOn
import monster.greyde.kachalochka.core.domain.gym.shownOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Counts [forVisit] calls, so a test can assert an ordinary day reads its sets once. */
private class CountingWorkoutSets(
    private val delegate: WorkoutSetRepository,
) : WorkoutSetRepository by delegate {
    var forVisitCalls = 0
        private set

    override suspend fun forVisit(visitId: VisitId): List<WorkoutSet> {
        forVisitCalls++
        return delegate.forVisit(visitId)
    }
}

class ShownOnTest {
    private val database = inMemoryDatabase()
    private val visits =
        LocalVisitRepository(database, OutboxDao(database), Dispatchers.Unconfined)
    private val sets =
        CountingWorkoutSets(
            LocalWorkoutSetRepository(database, OutboxDao(database), Dispatchers.Unconfined),
        )
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val fourteenth = CalendarDay(2023, 11, 14)
    private val fifteenth = CalendarDay(2023, 11, 15)
    private val machine = MachineId.random()
    private val utc: (Instant) -> Duration = { Duration.ZERO }

    private fun visit(
        recordedAt: Instant,
        day: CalendarDay?,
    ) = Visit(VisitId.random(), null, day, recordedAt, t0, false)

    private fun set(visitId: VisitId) =
        WorkoutSet(WorkoutSetId.random(), null, visitId, machine, 70.0, 10, 0, t0, t0, false)

    private suspend fun shownOn(
        day: CalendarDay,
        utcOffset: (Instant) -> Duration = utc,
    ): Visit? = visits.shownOn(null, day, sets, utcOffset)?.visit

    @Test
    fun a_visit_no_client_has_dated_shows_on_the_day_it_was_recorded_on() =
        runTest {
            val undated = visit(t0, null)
            visits.upsert(undated)

            assertEquals(undated.copy(day = fourteenth), shownOn(fourteenth))
            assertNull(shownOn(fifteenth))
            assertEquals(undated.copy(day = fifteenth), shownOn(fifteenth) { 3.hours })
        }

    @Test
    fun of_a_dated_and_an_undated_visit_on_one_day_the_newest_shows() =
        runTest {
            val dated = visit(t0 - 1.hours, fourteenth)
            val undated = visit(t0, null)
            visits.upsert(dated)
            visits.upsert(undated)

            assertEquals(undated.copy(day = fourteenth), shownOn(fourteenth))

            visits.upsert(undated.copy(deleted = true))

            assertEquals(dated, shownOn(fourteenth))
        }

    @Test
    fun a_day_s_visits_are_the_live_ones_dated_on_it_or_recorded_on_it_undated() =
        runTest {
            val dated = visit(t0 - 1.hours, fourteenth)
            val undated = visit(t0, null)
            val removed = visit(t0 - 2.hours, fourteenth).copy(deleted = true)
            val nextDay = visit(t0 + 3.hours, null)
            val movedAway = visit(t0 - 3.hours, fifteenth)
            listOf(dated, undated, removed, nextDay, movedAway).forEach { visits.upsert(it) }

            assertEquals(setOf(dated, undated), visits.allOn(null, fourteenth, utc).toSet())
        }

    @Test
    fun an_earlier_visit_with_sets_shows_over_a_later_empty_one_with_those_sets() =
        runTest {
            val early = visit(t0 - 1.hours, fourteenth)
            val laterEmpty = visit(t0, fourteenth)
            val earlySet = set(early.id)
            visits.upsert(early)
            visits.upsert(laterEmpty)
            sets.upsert(earlySet)

            assertEquals(
                ShownVisit(early, listOf(earlySet)),
                visits.shownOn(null, fourteenth, sets, utc),
            )
        }

    @Test
    fun of_two_visits_with_sets_the_later_shows() =
        runTest {
            val early = visit(t0 - 1.hours, fourteenth)
            val later = visit(t0, fourteenth)
            visits.upsert(early)
            visits.upsert(later)
            sets.upsert(set(early.id))
            sets.upsert(set(later.id))

            assertEquals(later, shownOn(fourteenth))
        }

    @Test
    fun of_two_empty_visits_the_later_shows() =
        runTest {
            val early = visit(t0 - 1.hours, fourteenth)
            val later = visit(t0, fourteenth)
            visits.upsert(early)
            visits.upsert(later)

            assertEquals(later, shownOn(fourteenth))
        }

    @Test
    fun a_single_visit_on_a_day_shows_with_its_sets_read_once() =
        runTest {
            val only = visit(t0, fourteenth)
            val onlySet = set(only.id)
            visits.upsert(only)
            sets.upsert(onlySet)

            assertEquals(
                ShownVisit(only, listOf(onlySet)),
                visits.shownOn(null, fourteenth, sets, utc),
            )
            assertEquals(1, sets.forVisitCalls)
        }
}
