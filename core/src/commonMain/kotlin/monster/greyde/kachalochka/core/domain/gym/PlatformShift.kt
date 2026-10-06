package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

/** The platform weight a set's total has on top of its recorded weight. */
private val Machine.platformOffset: Double
    get() = if (platformIncluded) 0.0 else platformWeight

/**
 * What every weight recorded on [from], in [to]'s unit, changes by to keep its total under [to]'s
 * platform.
 */
fun platformShift(
    from: Machine,
    to: Machine,
): Double {
    val offset = convertedWeight(from.platformOffset, from.unit, to.unit, to.weightStep)
    return roundWeight(offset - to.platformOffset)
}

fun shiftedSets(
    sets: List<WorkoutSet>,
    shift: Double,
    now: Instant,
): List<WorkoutSet> =
    sets.map {
        it.copy(
            weight = roundWeight(it.weight + shift).coerceAtLeast(0.0),
            updatedAt = now,
        )
    }
