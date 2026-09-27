package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Duration
import kotlin.time.Instant

/** The day a visit falls on: its own, or the one it was recorded on where no client wrote one. */
fun Visit.dayAt(utcOffset: (Instant) -> Duration): CalendarDay =
    day ?: CalendarDay.of(recordedAt, utcOffset(recordedAt))

/**
 * The rows that leave one live visit per day: a missing day filled in, and of the visits sharing
 * a day all but the newest by [visitRecency] removed with their [sets]. Rows already in shape
 * are left out, so a second run returns nothing.
 */
fun normalizedVisits(
    visits: List<Visit>,
    sets: List<WorkoutSet>,
    utcOffset: (Instant) -> Duration,
    now: Instant,
): List<VisitRows> =
    visits
        .filterNot { it.deleted }
        .groupBy { it.dayAt(utcOffset) }
        .flatMap { (day, sameDay) ->
            val kept = sameDay.maxWith(visitRecency)
            sameDay.mapNotNull { visit ->
                when {
                    visit.id != kept.id ->
                        removedVisit(visit, sets.filter { it.visitId == visit.id }, now)

                    visit.day == null ->
                        VisitRows(visit.copy(day = day, updatedAt = now), emptyList())

                    else -> null
                }
            }
        }
