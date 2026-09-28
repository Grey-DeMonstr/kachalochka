package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit

data class SharedMachine(
    val machine: Machine,
    val sets: List<WorkoutSet>,
)

private val SHORT_WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

/** [machines] in visit order, each with its sets in visit order. */
fun visitShareText(
    nickname: String,
    day: CalendarDay,
    machines: List<SharedMachine>,
    preferred: PreferredWeightUnit,
): String {
    val header =
        listOf(nickname.trim(), SHORT_WEEKDAYS[day.dayOfWeek - 1])
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    val lines = machines.filter { it.sets.isNotEmpty() }.map { shareLine(it, preferred) }
    return (listOf(header, "") + lines).joinToString("\n")
}

private fun sharedUnit(
    machine: Machine,
    preferred: PreferredWeightUnit,
): String {
    val label = shownLabel(machine, preferred)
    return if (machine.unit == WeightUnit.Custom) " $label" else label
}

private fun shareLine(
    shared: SharedMachine,
    preferred: PreferredWeightUnit,
): String {
    val machine = shared.machine
    val platform =
        if (machine.platformWeight > 0 && !machine.platformIncluded) {
            val weight = formatNumber(shownWeight(machine.platformWeight, machine, preferred))
            " (+$weight${sharedUnit(machine, preferred)})"
        } else {
            ""
        }
    return "${machine.name}$platform ${setsSummary(machine, shared.sets, preferred)}"
}

/** The weights and reps of [sets] on [machine], written as a shared visit writes them. */
fun setsSummary(
    machine: Machine,
    sets: List<WorkoutSet>,
    preferred: PreferredWeightUnit,
): String {
    val unit = sharedUnit(machine, preferred)
    val weights = sets.map { shownWeight(it.weight, machine, preferred) }
    val reps = sets.map { it.reps }
    val side = if (machine.weightMode == WeightMode.PerSide) " на каждую," else ""
    val weightPart =
        when {
            weights.all { it == 0.0 } -> null
            weights.distinct().size == 1 -> formatNumber(weights.first()) + unit + side
            else -> weights.joinToString("-") { formatNumber(it) } + unit + side
        }
    val repsPart =
        if (reps.distinct().size == 1) "${reps.size}x${reps.first()}" else reps.joinToString("-")
    return listOfNotNull(weightPart, repsPart).joinToString(" ")
}
