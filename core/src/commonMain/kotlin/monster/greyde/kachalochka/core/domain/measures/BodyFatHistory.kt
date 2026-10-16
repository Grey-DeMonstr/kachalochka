package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.core.domain.profile.ageOn

/**
 * [method]'s percent on each day with a value of a measure it reads, oldest first, from the first
 * day all of them have one. On a day without its own value, a measure takes the line between the
 * values around that day, or its last value after them.
 */
fun bodyFatHistory(
    method: BodyFatMethod,
    sex: Sex?,
    birthDate: CalendarDay?,
    heightCm: Double?,
    values: Map<MeasureKind, Map<CalendarDay, Double>>,
): List<Pair<CalendarDay, Double>> {
    val series =
        MeasureKind.entries
            .filter { method in methodsReading(it, sex) }
            .associateWith { values[it].orEmpty().toList().sortedBy { (day, _) -> day } }
    if (series.values.any { it.isEmpty() }) return emptyList()
    val start = series.values.maxOf { it.first().first }
    val days =
        series.values
            .flatten()
            .map { it.first }
            .filter { it >= start }
            .distinct()
            .sorted()
    return days.mapNotNull { day ->
        fun on(kind: MeasureKind): Double? = series[kind]?.let { valueOn(it, day) }
        val inputs =
            BodyInputs(
                sex = sex,
                age = birthDate?.let { ageOn(it, day) },
                heightCm = heightCm,
                weightKg = on(MeasureKind.Weight),
                waistCm = on(MeasureKind.Waist),
                neckCm = on(MeasureKind.Neck),
                hipsCm = on(MeasureKind.Hips),
            )
        bodyFat(method, inputs)?.let { day to it }
    }
}

/** [series] is oldest first and starts on or before [day]. */
private fun valueOn(
    series: List<Pair<CalendarDay, Double>>,
    day: CalendarDay,
): Double {
    val next = series.indexOfFirst { it.first >= day }
    if (next < 0) return series.last().second
    val (nextDay, nextValue) = series[next]
    if (nextDay == day) return nextValue
    val (previousDay, previousValue) = series[next - 1]
    val share =
        (day.epochDay - previousDay.epochDay).toDouble() /
            (nextDay.epochDay - previousDay.epochDay)
    return previousValue + (nextValue - previousValue) * share
}
