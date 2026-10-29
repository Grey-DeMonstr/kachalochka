package monster.greyde.kachalochka.core.domain.gym

/** A machine's result on one day: its best set and how many of the day's sets equalled it. */
data class DayBest(
    val weight: Double,
    val reps: Int,
    val sets: Int,
) {
    companion object {
        /** Stored on a machine's first day, where there is no earlier result. */
        val NONE = DayBest(0.0, 0, 0)
    }
}

/** Weaker first: by weight, lighter is stronger on a gravitron, then reps, then sets. */
fun dayBestOrder(mode: WeightMode): Comparator<DayBest> =
    compareBy<DayBest> { if (mode == WeightMode.Counterweight) -it.weight else it.weight }
        .thenBy { it.reps }
        .thenBy { it.sets }

/** The day's best set and how many of [sets] equal it; null without a live set. */
fun dayBest(
    sets: List<WorkoutSet>,
    mode: WeightMode,
): DayBest? {
    val live = sets.filterNot { it.deleted }
    val best = bestSet(live, mode) ?: return null
    return DayBest(
        best.weight,
        best.reps,
        live.count { it.weight == best.weight && it.reps == best.reps },
    )
}
