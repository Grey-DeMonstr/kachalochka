package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay

/** [months] is null for every value ever recorded. */
enum class MeasurePeriod(
    val months: Int?,
) {
    Month(1),
    Quarter(3),
    HalfYear(6),
    Year(12),
    All(null),
}

/**
 * The first day of [period] ending [today]: today's day of the month [MeasurePeriod.months] back,
 * or that month's last day when it is shorter. Null for [MeasurePeriod.All].
 */
fun periodStart(
    period: MeasurePeriod,
    today: CalendarDay,
): CalendarDay? = period.months?.let { today.minusMonths(it) }

/** The values of [period] ending [today], oldest first. */
fun inPeriod(
    values: List<Measurement>,
    period: MeasurePeriod,
    today: CalendarDay,
): List<Measurement> {
    val oldestFirst = values.sortedBy { it.day }
    val start = periodStart(period, today) ?: return oldestFirst
    return oldestFirst.filter { it.day >= start }
}
