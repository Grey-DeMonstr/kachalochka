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
 * The values of [period] ending [today], oldest first. A period starts on today's day of the
 * month [MeasurePeriod.months] back, or on that month's last day when it is shorter.
 */
fun inPeriod(
    values: List<Measurement>,
    period: MeasurePeriod,
    today: CalendarDay,
): List<Measurement> {
    val oldestFirst = values.sortedBy { it.day }
    val months = period.months ?: return oldestFirst
    val start = today.minusMonths(months)
    return oldestFirst.filter { it.day >= start }
}
