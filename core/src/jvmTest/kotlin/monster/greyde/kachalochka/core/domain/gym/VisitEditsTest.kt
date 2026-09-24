package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class VisitEditsTest {
    private val now = T0 + 10.days
    private val ended = Visit(VISIT_A, null, T0, T0 + 1.hours, T0, false)
    private val fifth = CalendarDay(2023, 11, 5)

    @Test
    fun a_past_visit_is_ended_at_local_noon_of_its_day() {
        val visit = pastVisit(CalendarDay(2023, 11, 10), null, 3.hours, now)

        assertEquals(Instant.parse("2023-11-10T09:00:00Z"), visit.recordedAt)
        assertEquals(visit.recordedAt, visit.endedAt)
        assertEquals(now, visit.updatedAt)
        assertFalse(visit.deleted)
    }

    @Test
    fun moving_keeps_every_clock_time_and_the_visit_s_length() {
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 300), set(VISIT_A, 70.0, atSeconds = 600))

        val moved = movedVisit(ended, sets, fifth, Duration.ZERO, now)

        assertEquals(Instant.parse("2023-11-05T22:13:20Z"), moved.visit.recordedAt)
        assertEquals(Instant.parse("2023-11-05T23:13:20Z"), moved.visit.endedAt)
        assertEquals(
            listOf("2023-11-05T22:18:20Z", "2023-11-05T22:23:20Z").map(Instant::parse),
            moved.sets.map { it.recordedAt },
        )
        assertEquals(listOf(now, now), moved.sets.map { it.updatedAt })
        assertEquals(now, moved.visit.updatedAt)
    }

    @Test
    fun sets_recorded_after_midnight_stay_after_the_others() {
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 0), set(VISIT_A, 70.0, atSeconds = 7_200))

        val moved = movedVisit(ended, sets, fifth, Duration.ZERO, now)

        assertEquals(
            listOf("2023-11-05T22:13:20Z", "2023-11-06T00:13:20Z").map(Instant::parse),
            moved.sets.map { it.recordedAt },
        )
    }

    @Test
    fun a_half_finished_move_converges_when_repeated() {
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 0), set(VISIT_A, 70.0, atSeconds = 600))
        val full = movedVisit(ended, sets, fifth, Duration.ZERO, now)
        val half = listOf(sets[0], full.sets[1])

        assertEquals(full, movedVisit(ended, half, fifth, Duration.ZERO, now))
        assertEquals(full, movedVisit(full.visit, full.sets, fifth, Duration.ZERO, now))
    }

    @Test
    fun removing_a_visit_removes_every_set() {
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 0), set(VISIT_A, 70.0, atSeconds = 600))

        val removed = removedVisit(ended, sets, now)

        assertEquals(true, removed.visit.deleted)
        assertEquals(listOf(true, true), removed.sets.map { it.deleted })
        assertEquals(listOf(now, now), removed.sets.map { it.updatedAt })
        assertEquals(now, removed.visit.updatedAt)
    }

    @Test
    fun a_running_visit_records_now_and_an_ended_one_after_its_last_set() {
        val running = ended.copy(endedAt = null)
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 0), set(VISIT_A, 70.0, atSeconds = 600))

        assertEquals(now, recordingInstant(running, sets, now))
        assertEquals(sets.last().recordedAt + 1.seconds, recordingInstant(ended, sets, now))
        assertEquals(ended.recordedAt + 1.seconds, recordingInstant(ended, emptyList(), now))
    }
}
