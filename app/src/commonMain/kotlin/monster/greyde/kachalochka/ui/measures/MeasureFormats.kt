package monster.greyde.kachalochka.ui.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.strings.AppStrings
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

fun fatPercent(value: Double): String = "${oneDecimal(value)} %"

/** "Последний замер": the day alone for today and yesterday, else the date and the days since. */
fun lastMeasured(
    day: CalendarDay,
    today: CalendarDay,
): String {
    val days = (today.epochDay - day.epochDay).toInt().coerceAtLeast(0)
    val ago = daysAgoLabel(days)
    return AppStrings.current.lastMeasured(
        if (days <= 1) ago else "${dayMonthLabel(day, today.year)}, $ago",
    )
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
