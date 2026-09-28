package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import kotlin.math.round

data class SharedMachine(
    val machine: Machine,
    val sets: List<WorkoutSet>,
)

private val SHORT_WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
private const val KG_PER_LB = 0.45359237

/** Friends read kilograms; pounds are converted to the nearest half kilogram. */
private fun inKg(
    weight: Double,
    unit: WeightUnit,
): Double = if (unit == WeightUnit.Lb) round(weight * KG_PER_LB * 2) / 2 else weight

/** [machines] in visit order, each with its sets in visit order. */
fun visitShareText(
    nickname: String,
    day: CalendarDay,
    machines: List<SharedMachine>,
): String {
    val header =
        listOf(nickname.trim(), SHORT_WEEKDAYS[day.dayOfWeek - 1])
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    val lines = machines.filter { it.sets.isNotEmpty() }.map(::shareLine)
    return (listOf(header, "") + lines).joinToString("\n")
}

private fun sharedUnit(machine: Machine): String =
    if (machine.unit == WeightUnit.Custom) " ${unitLabel(machine)}" else "кг"

private fun shareLine(shared: SharedMachine): String {
    val machine = shared.machine
    val platform =
        if (machine.platformWeight > 0 && !machine.platformIncluded) {
            " (+${formatNumber(inKg(machine.platformWeight, machine.unit))}${sharedUnit(machine)})"
        } else {
            ""
        }
    return "${machine.name}$platform ${setsSummary(machine, shared.sets)}"
}

/** The weights and reps of [sets] on [machine], written as a shared visit writes them. */
fun setsSummary(
    machine: Machine,
    sets: List<WorkoutSet>,
): String {
    val unit = sharedUnit(machine)
    val weights = sets.map { inKg(it.weight, machine.unit) }
    val reps = sets.map { it.reps }
    val weightPart =
        when {
            weights.all { it == 0.0 } -> null
            weights.distinct().size == 1 -> formatNumber(weights.first()) + unit
            else -> weights.joinToString("-") { formatNumber(it) } + unit
        }
    val repsPart =
        if (reps.distinct().size == 1) "${reps.size}x${reps.first()}" else reps.joinToString("-")
    return listOfNotNull(weightPart, repsPart).joinToString(" ")
}
