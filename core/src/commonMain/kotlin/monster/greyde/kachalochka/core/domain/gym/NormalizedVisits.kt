package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Duration
import kotlin.time.Instant

/** The day a visit falls on: its own, or the one it was recorded on where no client wrote one. */
fun Visit.dayAt(utcOffset: (Instant) -> Duration): CalendarDay =
    day ?: CalendarDay.of(recordedAt, utcOffset(recordedAt))

/**
 * Of one day's live visits, the one [normalizedVisits] keeps and [VisitRepository.shownOn] shows:
 * the newest by [visitRecency] of those [hasLiveSets] marks, or of all when none is marked. 1.0.1
 * wrote a visit on opening it, so an empty one may follow the day's workout.
 */
fun keptVisit(
    sameDay: List<Visit>,
    hasLiveSets: (VisitId) -> Boolean,
): Visit {
    val withSets = sameDay.filter { hasLiveSets(it.id) }
    return withSets.ifEmpty { sameDay }.maxWith(visitRecency)
}

/** The visit a day shows, with its live sets in [visitOrder]. */
data class ShownVisit(
    val visit: Visit,
    val sets: List<WorkoutSet>,
)

/**
 * Of one day's live visits, the [keptVisit] and its sets. [setsOf] is asked about every visit only
 * when more than one is in play, so an ordinary day costs one read.
 */
suspend fun shownVisit(
    sameDay: List<Visit>,
    setsOf: suspend (Visit) -> List<WorkoutSet>,
): ShownVisit? {
    val only = sameDay.singleOrNull()
    if (only != null) return ShownVisit(only, setsOf(only))
    if (sameDay.isEmpty()) return null
    val setsByVisit = sameDay.associate { it.id to setsOf(it) }
    val kept = keptVisit(sameDay) { setsByVisit[it]?.isNotEmpty() == true }
    return ShownVisit(kept, setsByVisit.getValue(kept.id))
}

/**
 * The rows that leave one live visit per day: a missing day filled in, and of the visits sharing
 * a day all but the [keptVisit] removed with their [sets]. Rows already in shape are left out, so
 * a second run returns nothing.
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
            val kept = keptVisit(sameDay) { it in liveSets }
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
