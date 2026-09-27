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
    private val fourteenth = CalendarDay(2023, 11, 14)
    private val past = Visit(VISIT_A, null, fourteenth, T0, T0, false)
    private val fifth = CalendarDay(2023, 11, 5)

    @Test
    fun another_day_s_visit_is_recorded_at_its_local_noon_and_today_s_now() {
        val tenth = dayVisit(CalendarDay(2023, 11, 10), null, fourteenth, 3.hours, now)

        assertEquals(CalendarDay(2023, 11, 10), tenth.day)
        assertEquals(Instant.parse("2023-11-10T09:00:00Z"), tenth.recordedAt)
        assertEquals(now, tenth.updatedAt)
        assertFalse(tenth.deleted)
        assertEquals(now, dayVisit(fourteenth, null, fourteenth, 3.hours, now).recordedAt)
    }

    @Test
    fun moving_keeps_every_clock_time_and_takes_the_new_day() {
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 300), set(VISIT_A, 70.0, atSeconds = 600))

        val moved = movedVisit(past, sets, fifth, Duration.ZERO, now)

        assertEquals(Instant.parse("2023-11-05T22:13:20Z"), moved.visit.recordedAt)
        assertEquals(fifth, moved.visit.day)
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

        val moved = movedVisit(past, sets, fifth, Duration.ZERO, now)

        assertEquals(
            listOf("2023-11-05T22:13:20Z", "2023-11-06T00:13:20Z").map(Instant::parse),
            moved.sets.map { it.recordedAt },
        )
    }

    @Test
    fun a_half_finished_move_converges_when_repeated() {
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 0), set(VISIT_A, 70.0, atSeconds = 600))
        val full = movedVisit(past, sets, fifth, Duration.ZERO, now)
        val half = listOf(sets[0], full.sets[1])

        assertEquals(full, movedVisit(past, half, fifth, Duration.ZERO, now))
        assertEquals(full, movedVisit(full.visit, full.sets, fifth, Duration.ZERO, now))
    }

    @Test
    fun removing_a_visit_removes_every_set() {
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 0), set(VISIT_A, 70.0, atSeconds = 600))

        val removed = removedVisit(past, sets, now)

        assertEquals(true, removed.visit.deleted)
        assertEquals(listOf(true, true), removed.sets.map { it.deleted })
        assertEquals(listOf(now, now), removed.sets.map { it.updatedAt })
        assertEquals(now, removed.visit.updatedAt)
    }

    @Test
    fun today_s_visit_records_now_and_another_day_s_after_its_last_set() {
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 0), set(VISIT_A, 70.0, atSeconds = 600))

        assertEquals(now, recordingInstant(past, sets, fourteenth, now))
        assertEquals(sets.last().recordedAt + 1.seconds, recordingInstant(past, sets, fifth, now))
        assertEquals(past.recordedAt + 1.seconds, recordingInstant(past, emptyList(), fifth, now))
    }
}
