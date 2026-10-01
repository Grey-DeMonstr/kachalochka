package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Duration
import kotlin.time.Instant

/** A number of months back from today. */
enum class StatsPeriod(
    val months: Int,
) {
    Month(1),
    ThreeMonths(3),
    SixMonths(6),
    Year(12),
    ;

    fun start(today: CalendarDay): CalendarDay = today.minusMonths(months)
}

/**
 * A machine's progress over a period: from the best set before it, when there is one, else from
 * the period's worst set, to the period's best.
 */
data class MachineProgress(
    val from: WorkoutSet,
    val to: WorkoutSet,
    val sinceBefore: Boolean,
)

/** Null when no live set falls on or after [start]. */
fun machineProgress(
    sets: List<WorkoutSet>,
    mode: WeightMode,
    start: Instant,
): MachineProgress? {
    val (inPeriod, before) = sets.filterNot { it.deleted }.partition { it.recordedAt >= start }
    val best = bestSet(inPeriod, mode) ?: return null
    val earlier = bestSet(before, mode)
    return if (earlier != null) {
        MachineProgress(earlier, best, sinceBefore = true)
    } else {
        MachineProgress(checkNotNull(worstSet(inPeriod, mode)), best, sinceBefore = false)
    }
}

/** How much stronger [to] is than [from]; on a gravitron, less weight is the gain. */
fun weightGain(
    from: Double,
    to: Double,
    mode: WeightMode,
): Double = if (mode == WeightMode.Counterweight) from - to else to - from

/** Each day's best live set, oldest day first. */
fun bestPerDay(
    sets: List<WorkoutSet>,
    mode: WeightMode,
    utcOffset: (Instant) -> Duration,
): List<Pair<CalendarDay, WorkoutSet>> =
    sets
        .filterNot { it.deleted }
        .groupBy { CalendarDay.of(it.recordedAt, utcOffset(it.recordedAt)) }
        .mapNotNull { (day, onDay) -> bestSet(onDay, mode)?.let { day to it } }
        .sortedBy { it.first }
