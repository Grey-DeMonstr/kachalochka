package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.shownOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ShownVisitTest {
    private val database = inMemoryDatabase()
    private val visits =
        LocalVisitRepository(database, OutboxDao(database), Dispatchers.Unconfined)
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val fourteenth = CalendarDay(2023, 11, 14)
    private val fifteenth = CalendarDay(2023, 11, 15)
    private val utc: (Instant) -> Duration = { Duration.ZERO }

    private fun visit(
        recordedAt: Instant,
        day: CalendarDay?,
    ) = Visit(VisitId.random(), null, day, recordedAt, t0, false)

    @Test
    fun a_visit_no_client_has_dated_shows_on_the_day_it_was_recorded_on() =
        runTest {
            val undated = visit(t0, null)
            visits.upsert(undated)

            assertEquals(undated.copy(day = fourteenth), visits.shownOn(null, fourteenth, utc))
            assertNull(visits.shownOn(null, fifteenth, utc))
            assertEquals(
                undated.copy(day = fifteenth),
                visits.shownOn(null, fifteenth) { 3.hours },
            )
        }

    @Test
    fun of_a_dated_and_an_undated_visit_on_one_day_the_one_normalization_keeps_shows() =
        runTest {
            val dated = visit(t0 - 1.hours, fourteenth)
            val undated = visit(t0, null)
            visits.upsert(dated)
            visits.upsert(undated)

            assertEquals(undated.copy(day = fourteenth), visits.shownOn(null, fourteenth, utc))

            visits.upsert(undated.copy(deleted = true))

            assertEquals(dated, visits.shownOn(null, fourteenth, utc))
        }
}
