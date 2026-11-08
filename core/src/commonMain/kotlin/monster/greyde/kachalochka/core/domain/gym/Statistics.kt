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
 * the period's worst set, to the period's best. [fromSets] and [toSets] count the sets equal to
 * each end in its visit, as a visit card counts a day's best.
 */
data class MachineProgress(
    val from: WorkoutSet,
    val to: WorkoutSet,
    val sinceBefore: Boolean,
    val fromSets: Int,
    val toSets: Int,
)

/** Null when no live set falls on or after [start]. */
fun machineProgress(
    sets: List<WorkoutSet>,
    mode: WeightMode,
    start: Instant,
): MachineProgress? {
    val live = sets.filterNot { it.deleted }
    val (inPeriod, before) = live.partition { it.recordedAt >= start }
    val best = bestSet(inPeriod, mode) ?: return null
    val from = bestSet(before, mode) ?: checkNotNull(worstSet(inPeriod, mode))

    fun equalInVisit(set: WorkoutSet) =
        live.count { it.visitId == set.visitId && it.weight == set.weight && it.reps == set.reps }
    return MachineProgress(
        from,
        best,
        sinceBefore = before.isNotEmpty(),
        fromSets = equalInVisit(from),
        toSets = equalInVisit(best),
    )
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

/**
 * [StatsSort.Growth] ranks as a visit card weighs a gain: by the weight gained, then the reps,
 * then the sets, then as [MachineSort.Recent]; machines without progress in the period come last.
 */
fun statsOrder(
    sort: StatsSort,
    peaks: Map<MachineId, MachinePeaks>,
    progress: Map<MachineId, MachineProgress>,
): Comparator<Machine> {
    sort.machineSort?.let { return machineOrder(it, peaks) }
    return compareByDescending<Machine> { m ->
        progress[m.id]?.let { weightGain(it.from.weight, it.to.weight, m.weightMode) }
    }.thenByDescending { m -> progress[m.id]?.let { it.to.reps - it.from.reps } }
        .thenByDescending { m -> progress[m.id]?.let { it.toSets - it.fromSets } }
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
