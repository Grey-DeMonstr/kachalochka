package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.TagSection
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.tagSections
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.strings.AppStrings

data class SharedMachine(
    val machine: Machine,
    val sets: List<WorkoutSet>,
)

/** [machines] in visit order, each with its sets in visit order. */
fun visitShareText(
    nickname: String,
    day: CalendarDay,
    machines: List<SharedMachine>,
    preferred: PreferredWeightUnit,
    groupByTag: Boolean = false,
): String {
    val header =
        listOf(nickname.trim(), AppStrings.current.weekdayShared(day.dayOfWeek))
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    val shown = machines.filter { it.sets.isNotEmpty() }
    val sections =
        if (groupByTag) {
            tagSections(
                shown,
            ) { it.machine.tags }
        } else {
            listOf(TagSection(emptySet(), shown))
        }
    val body =
        sections.joinToString("\n\n") { section ->
            val title = if (section.tags.isEmpty()) emptyList() else listOf(tagTitle(section.tags))
            (title + section.items.map { shareLine(it, preferred) }).joinToString("\n")
        }
    return "$header\n\n$body"
}

/** A tag set's heading, the same wherever it is read. */
fun tagTitle(tags: Set<String>): String = tags.sortedBy { it.lowercase() }.joinToString(", ")

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
    val side =
        if (machine.weightMode ==
            WeightMode.PerSide
        ) {
            AppStrings.current.perSideShared
        } else {
            ""
        }
    val sign = counterweightSign(machine)
    val weightPart =
        when {
            weights.all { it == 0.0 } -> null
            weights.distinct().size == 1 -> sign + formatNumber(weights.first()) + unit + side
            else -> sign + weights.joinToString("-") { formatNumber(it) } + unit + side
        }
    val repsPart =
        if (reps.distinct().size == 1) "${reps.size}x${reps.first()}" else reps.joinToString("-")
    return listOfNotNull(weightPart, repsPart).joinToString(" ")
}
