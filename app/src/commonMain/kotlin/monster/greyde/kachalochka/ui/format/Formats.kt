package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.time.Duration

fun formatNumber(value: Double): String {
    val rounded = round(value * 1000) / 1000
    val text = if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
    return text.replace('.', ',')
}

// A typed weight must read like a plain decimal, not any string a JVM Double parses:
// scientific notation, hex floats or a trailing unit would round-trip to Infinity or NaN
// and break sync, which cannot encode either as JSON.
private val plainDecimal = Regex("""\d*[.,]?\d*""")

fun parseDecimal(text: String): Double? {
    val trimmed = text.trim()
    if (!trimmed.any { it.isDigit() } || !plainDecimal.matches(trimmed)) return null
    return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

fun unitLabel(
    unit: WeightUnit,
    customLabel: String,
): String =
    when (unit) {
        WeightUnit.Kg -> "кг"
        WeightUnit.Lb -> "lb"
        // Only a row written elsewhere can leave the name blank; the form requires one.
        WeightUnit.Custom -> customLabel.trim().ifEmpty { "ед." }
    }

fun unitLabel(machine: Machine): String = unitLabel(machine.unit, machine.unitLabel)

private fun modeLabel(mode: WeightMode): String =
    when (mode) {
        WeightMode.Total -> "всего"
        WeightMode.PerSide -> "на сторону"
    }

fun weightCaption(machine: Machine): String {
    val unit = unitLabel(machine)
    val mode = modeLabel(machine.weightMode)
    return "$unit $mode · ±${formatNumber(machine.weightStep)}"
}

fun platformSuffix(machine: Machine): String? =
    if (machine.platformWeight > 0 && !machine.platformIncluded) {
        "(+${formatNumber(machine.platformWeight)} ${unitLabel(machine)})"
    } else {
        null
    }

fun machineTitle(machine: Machine): String =
    platformSuffix(machine)?.let { "${machine.name} $it" } ?: machine.name

fun setValue(
    weight: Double,
    reps: Int,
    unit: String,
): String = "${formatNumber(weight)} $unit × $reps"

fun shortSet(
    weight: Double,
    reps: Int,
): String = "${formatNumber(weight)}×$reps"

fun groupSummary(
    sets: List<WorkoutSet>,
    unit: String,
): String {
    val weights = sets.map { it.weight }
    return if (weights.size > 1 && weights.distinct().size == 1) {
        "${weights.size} × ${formatNumber(weights.first())} $unit"
    } else {
        weights.joinToString(", ") { formatNumber(it) } + " $unit"
    }
}

fun pluralRu(
    n: Int,
    one: String,
    few: String,
    many: String,
): String {
    val lastTwo = n % 100
    val last = n % 10
    return when {
        lastTwo in 11..14 -> many
        last == 1 -> one
        last in 2..4 -> few
        else -> many
    }
}

fun setCount(n: Int): String = "$n ${pluralRu(n, "подход", "подхода", "подходов")}"

fun machineCount(n: Int): String = "$n ${pluralRu(n, "тренажёр", "тренажёра", "тренажёров")}"

fun daysAgoLabel(days: Int): String =
    when (days) {
        0 -> "сегодня"
        1 -> "вчера"
        else -> "$days ${pluralRu(days, "день", "дня", "дней")} назад"
    }

private val monthsGenitive =
    listOf(
        "января",
        "февраля",
        "марта",
        "апреля",
        "мая",
        "июня",
        "июля",
        "августа",
        "сентября",
        "октября",
        "ноября",
        "декабря",
    )

fun monthGenitive(month: Int): String = monthsGenitive[month - 1]

fun dayMonthLabel(
    day: CalendarDay,
    currentYear: Int,
): String {
    val label = "${day.day} ${monthGenitive(day.month)}"
    return if (day.year == currentYear) label else "$label ${day.year}"
}

private val monthsNominative =
    listOf(
        "Январь",
        "Февраль",
        "Март",
        "Апрель",
        "Май",
        "Июнь",
        "Июль",
        "Август",
        "Сентябрь",
        "Октябрь",
        "Ноябрь",
        "Декабрь",
    )

fun monthTitle(month: CalendarMonth): String = "${monthsNominative[month.month - 1]} ${month.year}"

private val weekdayNames =
    listOf(
        "Понедельник",
        "Вторник",
        "Среда",
        "Четверг",
        "Пятница",
        "Суббота",
        "Воскресенье",
    )

fun weekdayName(dayOfWeek: Int): String = weekdayNames[dayOfWeek - 1]

val WEEKDAY_LABELS: List<String> = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

fun isoDate(day: CalendarDay): String {
    val month = day.month.toString().padStart(2, '0')
    val date = day.day.toString().padStart(2, '0')
    return "${day.year}-$month-$date"
}

fun clockLabel(minuteOfDay: Int): String {
    val hours = (minuteOfDay / 60).toString().padStart(2, '0')
    val minutes = (minuteOfDay % 60).toString().padStart(2, '0')
    return "$hours:$minutes"
}

fun formatRest(remaining: Duration): String {
    val total = ceil(remaining.inWholeMilliseconds / 1000.0).toLong().coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** [person] is the name of the account a set would be recorded as, when there is a choice. */
fun saveLabel(
    person: String?,
    editing: Boolean,
): String =
    when {
        editing -> "Сохранить"
        person == null -> "Сохранить подход"
        else -> "Сохранить · $person"
    }

/** The avatar draws a letter, never a photo. */
fun monogram(name: String): String =
    name
        .trim()
        .take(1)
        .uppercase()
        .ifBlank { "?" }
