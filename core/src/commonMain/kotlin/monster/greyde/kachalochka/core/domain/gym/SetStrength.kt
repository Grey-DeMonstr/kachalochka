package monster.greyde.kachalochka.core.domain.gym

/** Weaker first: by weight, lighter is stronger on a gravitron, then by reps. */
fun setStrength(mode: WeightMode): Comparator<WorkoutSet> =
    compareBy<WorkoutSet> { if (mode == WeightMode.Counterweight) -it.weight else it.weight }
        .thenBy { it.reps }

/** The record's pick among [sets]: the heaviest with the most reps, the lightest on a gravitron. */
fun bestSet(
    sets: List<WorkoutSet>,
    mode: WeightMode,
): WorkoutSet? = sets.maxWithOrNull(setStrength(mode))

fun worstSet(
    sets: List<WorkoutSet>,
    mode: WeightMode,
): WorkoutSet? = sets.minWithOrNull(setStrength(mode))
