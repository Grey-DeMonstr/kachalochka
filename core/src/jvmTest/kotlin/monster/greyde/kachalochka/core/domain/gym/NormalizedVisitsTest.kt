package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NormalizedVisitsTest {
    private val now = T0 + 1.days
    private val fourteenth = CalendarDay(2023, 11, 14)
    private val utc: (Instant) -> Duration = { Duration.ZERO }

    private fun visit(
        id: VisitId,
        day: CalendarDay?,
        atSeconds: Long,
        updatedAt: Instant = T0,
    ) = Visit(id, null, day, T0 + atSeconds.seconds, updatedAt, false)

    @Test
    fun a_visit_without_a_day_gets_the_day_it_was_recorded_on() {
        val old = visit(VISIT_A, null, 0)

        val rows = normalizedVisits(listOf(old), emptyList(), { 3.hours }, now)

        val filled = old.copy(day = CalendarDay(2023, 11, 15), updatedAt = now)
        assertEquals(listOf(VisitRows(filled, emptyList())), rows)
    }

    @Test
    fun a_missing_day_is_read_at_the_offset_in_force_when_the_visit_was_recorded() {
        val old = visit(VISIT_A, null, 0)
        val cutOver = T0 + 1.hours
        val offset: (Instant) -> Duration = { if (it < cutOver) 1.hours else 3.hours }

        val rows = normalizedVisits(listOf(old), emptyList(), offset, now)

        assertEquals(listOf(fourteenth), rows.map { it.visit.day })
    }

    @Test
    fun of_several_visits_on_one_day_the_newest_stays_and_the_rest_go_with_their_sets() {
        val morning = visit(VISIT_A, fourteenth, -3_600)
        val evening = visit(VISIT_B, fourteenth, 0)
        val morningSets =
            listOf(set(VISIT_A, 60.0, atSeconds = -3_500), set(VISIT_A, 70.0, atSeconds = -3_400))
        val eveningSet = set(VISIT_B, 80.0, atSeconds = 60)

        val rows = normalizedVisits(listOf(evening, morning), morningSets + eveningSet, utc, now)

        assertEquals(listOf(removedVisit(morning, morningSets, now)), rows)
    }

    @Test
    fun a_later_visit_without_sets_gives_way_to_an_earlier_one_with_sets() {
        val workout = visit(VISIT_A, fourteenth, -3_600)
        val empty = visit(VISIT_B, fourteenth, 0)
        val workoutSet = set(VISIT_A, 60.0, atSeconds = -3_500)
        val removedSet = set(VISIT_B, 70.0, atSeconds = 60, deleted = true)

        val rows =
            normalizedVisits(listOf(empty, workout), listOf(workoutSet, removedSet), utc, now)

        assertEquals(listOf(removedVisit(empty, emptyList(), now)), rows)
    }

    @Test
    fun of_several_visits_without_sets_the_newest_stays() {
        val morning = visit(VISIT_A, fourteenth, -3_600)
        val evening = visit(VISIT_B, fourteenth, 0)

        val rows = normalizedVisits(listOf(evening, morning), emptyList(), utc, now)

        assertEquals(listOf(removedVisit(morning, emptyList(), now)), rows)
    }

    @Test
    fun a_tie_on_recording_time_goes_to_the_later_update_then_the_greater_id() {
        val first = visit(VISIT_A, fourteenth, 0)
        val updated = visit(VISIT_B, fourteenth, 0, updatedAt = T0 + 1.seconds)
        val greater = visit(VISIT_C, fourteenth, 0)

        val byUpdate = normalizedVisits(listOf(first, updated), emptyList(), utc, now)
        val byId = normalizedVisits(listOf(greater, first), emptyList(), utc, now)

        assertEquals(listOf(VISIT_A), byUpdate.map { it.visit.id })
        assertEquals(listOf(VISIT_A), byId.map { it.visit.id })
    }

    @Test
    fun a_visit_an_old_client_pushed_without_a_day_joins_the_day_it_was_recorded_on() {
        val old = visit(VISIT_A, null, -3_600)
        val current = visit(VISIT_B, fourteenth, 0)
        val oldSet = set(VISIT_A, 60.0, atSeconds = -3_500)
        val currentSet = set(VISIT_B, 70.0, atSeconds = 60)

        val rows = normalizedVisits(listOf(old, current), listOf(oldSet, currentSet), utc, now)

        assertEquals(listOf(removedVisit(old, listOf(oldSet), now)), rows)
    }

    @Test
    fun a_second_run_finds_nothing_to_write() {
        val visits =
            listOf(
                visit(VISIT_A, null, -3_600),
                visit(VISIT_B, fourteenth, 0),
                visit(VISIT_C, null, -86_400),
            )
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = -3_500))
        val first = normalizedVisits(visits, sets, utc, now)
        val written = first.map { it.visit }
        val visitsAfter = visits.map { v -> written.firstOrNull { it.id == v.id } ?: v }
        val setsAfter =
            sets.map { s -> first.flatMap { it.sets }.firstOrNull { it.id == s.id } ?: s }

        assertEquals(3, first.size)
        assertEquals(emptyList(), normalizedVisits(visitsAfter, setsAfter, utc, now))
    }

    @Test
    fun a_deleted_visit_counts_for_nothing() {
        val gone = visit(VISIT_A, fourteenth, 60).copy(deleted = true)
        val kept = visit(VISIT_B, fourteenth, 0)

        val rows = normalizedVisits(listOf(gone, kept), emptyList(), utc, now)

        assertEquals(emptyList(), rows)
    }
}
