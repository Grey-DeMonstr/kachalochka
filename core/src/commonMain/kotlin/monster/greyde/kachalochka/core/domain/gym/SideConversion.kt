package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

/**
 * What a weight recorded under [from] is multiplied by to read the same under [to]: a side is half
 * the total. A counterweight has no sides, so a change to or from it keeps the weight.
 */
fun sideFactor(
    from: WeightMode,
    to: WeightMode,
): Double =
    when {
        from == WeightMode.Total && to == WeightMode.PerSide -> 0.5
        from == WeightMode.PerSide && to == WeightMode.Total -> 2.0
        else -> 1.0
    }

fun sidedSets(
    sets: List<WorkoutSet>,
    factor: Double,
    now: Instant,
): List<WorkoutSet> =
    sets.map {
        it.copy(weight = roundWeight(it.weight * factor), updatedAt = now)
    }
