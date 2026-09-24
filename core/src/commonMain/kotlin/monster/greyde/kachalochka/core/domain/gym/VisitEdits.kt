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

fun pastVisit(
    day: CalendarDay,
    owner: UserId?,
    utcOffset: Duration,
    now: Instant,
): Visit {
    val noon = day.at(12.hours.inWholeMilliseconds, utcOffset)
    return Visit(VisitId.random(), owner, noon, noon, now, false)
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
    val endedAt = visit.endedAt?.let { recordedAt + (it - visit.recordedAt) }
    val shifted = visit.copy(recordedAt = recordedAt, endedAt = endedAt, updatedAt = now)
    return VisitRows(shifted, moved)
}

fun removedVisit(
    visit: Visit,
    sets: List<WorkoutSet>,
    now: Instant,
): VisitRows {
    val removed = sets.map { it.copy(deleted = true, updatedAt = now) }
    return VisitRows(visit.copy(deleted = true, updatedAt = now), removed)
}

/** A late addition to an ended visit stays on its day and in order. */
fun recordingInstant(
    visit: Visit,
    visitSets: List<WorkoutSet>,
    now: Instant,
): Instant =
    if (visit.endedAt == null) {
        now
    } else {
        (visitSets.maxOfOrNull { it.recordedAt } ?: visit.recordedAt) + 1.seconds
    }
