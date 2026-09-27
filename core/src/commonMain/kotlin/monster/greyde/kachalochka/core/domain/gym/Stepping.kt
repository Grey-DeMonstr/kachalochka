package monster.greyde.kachalochka.core.domain.gym

import kotlin.math.round

// Steps like 0.1 are not exact in binary, so each sum is rounded before it can accumulate error.
fun roundWeight(weight: Double): Double = round(weight * 1000) / 1000

fun stepWeight(
    weight: Double,
    step: Double,
    direction: Int,
): Double = roundWeight(weight + direction * step).coerceAtLeast(0.0)

fun stepReps(
    reps: Int,
    direction: Int,
): Int = (reps + direction).coerceAtLeast(1)
