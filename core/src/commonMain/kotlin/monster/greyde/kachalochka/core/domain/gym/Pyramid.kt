package monster.greyde.kachalochka.core.domain.gym

/** One set alone, or a pyramid's sets. */
data class SetGroup(
    val sets: List<WorkoutSet>,
    val pyramid: Boolean,
)

/**
 * Cuts one machine's [sets], in the order given, at each set that starts a pyramid. A pyramid
 * holds the sets up to the next start; the sets before the first start stand alone.
 */
fun pyramidGroups(sets: List<WorkoutSet>): List<SetGroup> {
    val groups = mutableListOf<SetGroup>()
    for (set in sets) {
        val current = groups.lastOrNull()
        if (current != null && current.pyramid && !set.pyramid) {
            groups[groups.lastIndex] = current.copy(sets = current.sets + set)
        } else {
            groups += SetGroup(listOf(set), set.pyramid)
        }
    }
    return groups
}

/** The first set of the last pyramid in [sets], which a new pyramid starts from. */
fun lastPyramidStart(sets: List<WorkoutSet>): WorkoutSet? = sets.lastOrNull { it.pyramid }
