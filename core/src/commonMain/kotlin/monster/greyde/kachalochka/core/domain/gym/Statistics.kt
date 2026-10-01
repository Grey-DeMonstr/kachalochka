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

/** The machine sorts, and [Growth]: the best improvement over the period first. */
enum class StatsSort(
    val machineSort: MachineSort?,
) {
    Recent(MachineSort.Recent),
    Name(MachineSort.Name),
    Frequent(MachineSort.Frequent),
    Growth(null),
}

/** The weight gained in percent of the starting weight; any gain from nothing is infinite. */
fun growth(
    progress: MachineProgress,
    mode: WeightMode,
): Double {
    val gain = weightGain(progress.from.weight, progress.to.weight, mode)
    return when {
        progress.from.weight != 0.0 -> gain / progress.from.weight * PERCENT
        gain > 0 -> Double.POSITIVE_INFINITY
        else -> 0.0
    }
}

private const val PERCENT = 100.0

/**
 * [StatsSort.Growth] ranks by [growth], then by reps gained, then as [MachineSort.Recent];
 * machines without progress in the period come last.
 */
fun statsOrder(
    sort: StatsSort,
    peaks: Map<MachineId, MachinePeaks>,
    progress: Map<MachineId, MachineProgress>,
): Comparator<Machine> {
    sort.machineSort?.let { return machineOrder(it, peaks) }
    return compareByDescending<Machine> { m -> progress[m.id]?.let { growth(it, m.weightMode) } }
        .thenByDescending { m -> progress[m.id]?.let { it.to.reps - it.from.reps } }
        .then(machineOrder(MachineSort.Recent, peaks))
}

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
