package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Duration
import kotlin.time.Instant

/** The day a visit falls on: its own, or the one it was recorded on where no client wrote one. */
fun Visit.dayAt(utcOffset: (Instant) -> Duration): CalendarDay =
    day ?: CalendarDay.of(recordedAt, utcOffset(recordedAt))

/**
 * The rows that leave one live visit per day: a missing day filled in, and of the visits sharing
 * a day all but one removed with their [sets]. The one kept is the newest by [visitRecency] of
 * those with a live set, or of all where none has one: 1.0.1 wrote a visit on opening it, so an
 * empty one may follow the day's workout. Rows already in shape are left out, so a second run
 * returns nothing.
 */
fun normalizedVisits(
    visits: List<Visit>,
    sets: List<WorkoutSet>,
    utcOffset: (Instant) -> Duration,
    now: Instant,
): List<VisitRows> {
    val liveSets = sets.filterNot { it.deleted }.groupBy { it.visitId }
    return visits
        .filterNot { it.deleted }
        .groupBy { it.dayAt(utcOffset) }
        .flatMap { (day, sameDay) ->
            val withSets = sameDay.filter { it.id in liveSets }
            val kept = withSets.ifEmpty { sameDay }.maxWith(visitRecency)
            sameDay.mapNotNull { visit ->
                when {
                    visit.id != kept.id ->
                        removedVisit(visit, liveSets[visit.id].orEmpty(), now)

                    visit.day == null ->
                        VisitRows(visit.copy(day = day, updatedAt = now), emptyList())

                    else -> null
                }
            }
        }
}
