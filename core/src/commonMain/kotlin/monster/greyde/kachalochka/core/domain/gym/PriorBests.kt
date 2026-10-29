package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

/**
 * One machine's sets with the right [WorkoutSet.priorBest]: only the live rows whose stored value
 * is wrong, stamped [now]. A set whose visit [dayOf] cannot place is left out.
 */
fun withPriorBests(
    sets: List<WorkoutSet>,
    dayOf: (VisitId) -> CalendarDay?,
    mode: WeightMode,
    now: Instant,
): List<WorkoutSet> {
    val byDay =
        sets
            .filterNot { it.deleted }
            .mapNotNull { set -> dayOf(set.visitId)?.let { it to set } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
    val order = dayBestOrder(mode)
    var running: DayBest? = null
    return buildList {
        byDay.values.forEach { daySets ->
            val prior = running ?: DayBest.NONE
            daySets
                .filter { it.priorBest != prior }
                .forEach { add(it.copy(priorBest = prior, updatedAt = now)) }
            val today = dayBest(daySets, mode) ?: return@forEach
            running = running?.let { maxOf(it, today, order) } ?: today
        }
    }
}
