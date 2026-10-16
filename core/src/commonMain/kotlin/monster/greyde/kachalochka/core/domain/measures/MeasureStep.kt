package monster.greyde.kachalochka.core.domain.measures

import kotlin.math.abs
import kotlin.math.round

private val steps = listOf(1.0, 0.5)
private const val FINEST = 0.1

/** A stored double is rarely an exact multiple, so a near one counts. */
private fun Double.isMultipleOf(step: Double): Boolean {
    val times = this / step
    return abs(times - round(times)) < 1e-6
}

/** The −/+ step: the finest of 1, 0.5 and 0.1 that [values] use, 1 with no values. */
fun measureStep(values: List<Double>): Double =
    steps.firstOrNull { step -> values.all { it.isMultipleOf(step) } } ?: FINEST
