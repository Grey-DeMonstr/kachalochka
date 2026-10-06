package monster.greyde.kachalochka.core.domain.gym

import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

const val KG_PER_LB: Double = 0.45359237

/** The weight steps of pound machines, largest first. */
private val LB_STEPS = listOf(5.0, 2.5, 1.25)

/** A recorded kilogram weight read off a pound stack lies this close to one of its plates. */
private const val LB_TOLERANCE = 1.0

/** An own unit has no known size, so only kilograms and pounds convert. */
fun convertible(
    from: WeightUnit,
    to: WeightUnit,
): Boolean = from != to && from != WeightUnit.Custom && to != WeightUnit.Custom

/** Pounds become kilograms to a tenth; kilograms become the nearest multiple of [lbStep]. */
fun convertedWeight(
    weight: Double,
    from: WeightUnit,
    to: WeightUnit,
    lbStep: Double,
): Double =
    when {
        !convertible(from, to) -> weight
        to == WeightUnit.Kg -> round(weight * KG_PER_LB * 10) / 10
        else -> roundWeight(round(weight / KG_PER_LB / lbStep) * lbStep)
    }

/**
 * The step of the pound stack [kgWeights] were most likely read from: the largest common step
 * that every weight lies near. Without weights, the step nearest to [kgStep].
 */
fun guessedLbStep(
    kgWeights: List<Double>,
    kgStep: Double,
): Double {
    if (kgWeights.isEmpty()) return LB_STEPS.minBy { abs(it - kgStep / KG_PER_LB) }
    val pounds = kgWeights.map { it / KG_PER_LB }
    return LB_STEPS.firstOrNull { step ->
        pounds.all { abs(it - round(it / step) * step) <= LB_TOLERANCE }
    } ?: LB_STEPS.last()
}

fun convertedSets(
    sets: List<WorkoutSet>,
    from: WeightUnit,
    to: WeightUnit,
    lbStep: Double,
    now: Instant,
): List<WorkoutSet> =
    sets.map { it.copy(weight = convertedWeight(it.weight, from, to, lbStep), updatedAt = now) }
