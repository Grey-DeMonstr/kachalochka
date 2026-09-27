package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Written sets first, then the visit, so a retried rewrite converges (tech spec §4.5). */
data class VisitRows(
    val visit: Visit,
    val sets: List<WorkoutSet>,
)

/** The row a day's first set creates: now for today, local noon for another day. */
fun dayVisit(
    day: CalendarDay,
    owner: UserId?,
    today: CalendarDay,
    utcOffset: Duration,
    now: Instant,
): Visit {
    val recordedAt = if (day == today) now else day.at(12.hours.inWholeMilliseconds, utcOffset)
    return Visit(VisitId.random(), owner, day, recordedAt, now, false)
}

fun movedVisit(
    visit: Visit,
    sets: List<WorkoutSet>,
    day: CalendarDay,
    utcOffset: Duration,
    now: Instant,
): VisitRows {
    val clock = millisOfDay(visit.recordedAt, utcOffset)
    val recordedAt = day.at(clock, utcOffset)
    // Placing by clock time, never by a day delta, makes a repeated move write the same rows.
    val moved =
        sets.map {
            val after = (millisOfDay(it.recordedAt, utcOffset) - clock).mod(MILLIS_PER_DAY)
            it.copy(recordedAt = recordedAt + after.milliseconds, updatedAt = now)
        }
    return VisitRows(visit.copy(day = day, recordedAt = recordedAt, updatedAt = now), moved)
}

fun removedVisit(
    visit: Visit,
    sets: List<WorkoutSet>,
    now: Instant,
): VisitRows {
    val removed = sets.map { it.copy(deleted = true, updatedAt = now) }
    return VisitRows(visit.copy(deleted = true, updatedAt = now), removed)
}

/** A late addition to another day's visit stays on its day and in order. */
fun recordingInstant(
    visit: Visit,
    visitSets: List<WorkoutSet>,
    today: CalendarDay,
    now: Instant,
): Instant =
    if (visit.day == today) {
        now
    } else {
        (visitSets.maxOfOrNull { it.recordedAt } ?: visit.recordedAt) + 1.seconds
    }
