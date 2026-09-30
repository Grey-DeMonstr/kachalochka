package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.time.Duration

fun formatNumber(value: Double): String {
    val rounded = round(value * 1000) / 1000
    return if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
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
        WeightUnit.Kg -> AppStrings.current.kg
        WeightUnit.Lb -> "lb"
        // Only a row written elsewhere can leave the name blank; the form requires one.
        WeightUnit.Custom -> customLabel.trim().ifEmpty { AppStrings.current.customUnitFallback }
    }

fun unitLabel(machine: Machine): String = unitLabel(machine.unit, machine.unitLabel)

private fun modeLabel(mode: WeightMode): String =
    when (mode) {
        WeightMode.Total -> AppStrings.current.modeTotal
        WeightMode.PerSide -> AppStrings.current.modePerSide
        WeightMode.Counterweight -> AppStrings.current.modeCounterweight
    }

fun weightCaption(
    machine: Machine,
    preferred: PreferredWeightUnit,
): String {
    val step = shownStep(machine.weightStep, machine.unit, shownUnit(machine, preferred))
    return "${shownLabel(machine, preferred)} ${modeLabel(machine.weightMode)} · " +
        "±${formatNumber(step)}"
}

/**
 * The weight being recorded is typed and stepped in the machine's own unit; this is it in the
 * unit weights are shown in, or null when that is the same.
 */
fun recordingConversion(
    machine: Machine,
    weight: Double,
    preferred: PreferredWeightUnit,
): String? {
    val shown = shownUnit(machine, preferred)
    if (shown == machine.unit) return null
    return "${formatNumber(shownWeight(weight, machine.unit, shown))} " +
        unitLabel(shown, machine.unitLabel)
}

fun friendMachineDetail(
    friend: FriendMachine,
    preferred: PreferredWeightUnit,
): String = "${friend.owner.displayName} · ${weightCaption(friend.machine, preferred)}"

fun platformSuffix(
    machine: Machine,
    preferred: PreferredWeightUnit,
): String? =
    if (machine.platformWeight > 0 && !machine.platformIncluded) {
        val weight = formatNumber(shownWeight(machine.platformWeight, machine, preferred))
        "(+$weight ${shownLabel(machine, preferred)})"
    } else {
        null
    }

fun machineTitle(
    machine: Machine,
    preferred: PreferredWeightUnit,
): String = platformSuffix(machine, preferred)?.let { "${machine.name} $it" } ?: machine.name

fun setValue(
    weight: Double,
    reps: Int,
    unit: String,
): String = "${formatNumber(weight)} $unit × $reps"

fun setValue(
    weight: Double,
    reps: Int,
    machine: Machine,
    preferred: PreferredWeightUnit,
): String =
    counterweightSign(machine) +
        setValue(shownWeight(weight, machine, preferred), reps, shownLabel(machine, preferred))

/** A gravitron's weight helps rather than loads, so it is written as a negative. */
fun counterweightSign(machine: Machine): String =
    if (machine.weightMode == WeightMode.Counterweight) "(-)" else ""

fun setCount(n: Int): String = AppStrings.current.sets(n)

fun machineCount(n: Int): String = AppStrings.current.machines(n)

fun memberCount(n: Int): String = AppStrings.current.members(n)

fun daysAgoLabel(days: Int): String = AppStrings.current.daysAgo(days)

fun dayMonthLabel(
    day: CalendarDay,
    currentYear: Int,
): String {
    val label = AppStrings.current.dayMonth(day.day, day.month)
    return if (day.year == currentYear) label else "$label ${day.year}"
}

fun weekdayDate(
    day: CalendarDay,
    withYear: Boolean,
): String {
    val label = "${weekdayName(day.dayOfWeek)}, ${AppStrings.current.dayMonth(day.day, day.month)}"
    return if (withYear) "$label ${day.year}" else label
}

fun monthTitle(month: CalendarMonth): String =
    "${AppStrings.current.monthTitle(month.month)} ${month.year}"

fun weekdayName(dayOfWeek: Int): String = AppStrings.current.weekday(dayOfWeek)

fun weekdayLabels(): List<String> = (1..7).map(AppStrings.current::weekdayShort)

fun isoDate(day: CalendarDay): String = day.iso

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
        editing -> AppStrings.current.save
        person == null -> AppStrings.current.saveSet
        else -> AppStrings.current.saveAs(person)
    }

/** The letter standing for [name] where no picture does. */
fun monogram(name: String): String =
    name
        .trim()
        .take(1)
        .uppercase()
        .ifBlank { "?" }
