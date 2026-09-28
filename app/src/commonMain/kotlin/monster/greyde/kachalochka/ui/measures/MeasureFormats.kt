package monster.greyde.kachalochka.ui.measures

import monster.greyde.kachalochka.ui.format.formatNumber
import kotlin.math.abs
import kotlin.math.round

private const val MINUS = '−'

fun measureValue(
    value: Double,
    unit: String,
): String = if (unit.isBlank()) formatNumber(value) else "${formatNumber(value)} $unit"

/** A positive [value] with exactly one decimal, as a calculated percent claims no more. */
fun oneDecimal(value: Double): String {
    val tenths = round(value * 10).toLong()
    return "${tenths / 10}.${tenths % 10}"
}

/** Rounded as [formatNumber] shows values, so two equal-looking values show no change. */
fun measureDelta(
    latest: Double,
    previous: Double,
): String? {
    val change = round((latest - previous) * 1000) / 1000
    if (change == 0.0) return null
    return (if (change > 0) "+" else "$MINUS") + formatNumber(abs(change))
}
