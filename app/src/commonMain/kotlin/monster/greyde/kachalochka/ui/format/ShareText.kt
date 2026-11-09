package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.pyramidGroups
import monster.greyde.kachalochka.core.domain.gym.sortedTags
import monster.greyde.kachalochka.core.domain.gym.tagSections
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.strings.AppStrings

data class SharedMachine(
    val machine: Machine,
    val sets: List<WorkoutSet>,
)

/**
 * What a visit's shared text is made of. It is read ahead of the tap: a browser lets the
 * clipboard be written only while a tap is recent, which a network read could outlast.
 */
data class SharedVisit(
    val nickname: String,
    val day: CalendarDay,
    val machines: List<SharedMachine>,
    val preferred: PreferredWeightUnit,
) {
    fun text(groupByTag: Boolean): String =
        visitShareText(nickname, day, machines, preferred, groupByTag)
}

/** [visitSets] grouped by machine in visit order, on the machines of [machines]. */
fun sharedMachines(
    visitSets: List<WorkoutSet>,
    machines: Map<MachineId, Machine>,
): List<SharedMachine> =
    groupByMachine(visitSets).mapNotNull { group ->
        machines[group.machineId]?.let { SharedMachine(it, group.sets) }
    }

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
        if (groupByTag) headedTagSections(shown) { it.machine.tags } else listOf(null to shown)
    val body =
        sections.joinToString("\n\n") { (title, items) ->
            (listOfNotNull(title) + items.map { shareLine(it, preferred) }).joinToString("\n")
        }
    return "$header\n\n$body"
}

/** A tag set's heading in capitals, as tags are drawn, the same wherever it is read. */
fun tagTitle(tags: Set<String>): String = sortedTags(tags).joinToString(", ") { it.uppercase() }

/** [tagSections] with their headings; untagged items are headed only beside tagged ones. */
fun <T> headedTagSections(
    items: List<T>,
    tagsOf: (T) -> Set<String>,
): List<Pair<String?, List<T>>> {
    val sections = tagSections(items, tagsOf)
    return sections.map { section ->
        val title =
            when {
                section.tags.isNotEmpty() -> tagTitle(section.tags)
                sections.size > 1 -> AppStrings.current.untaggedSection
                else -> null
            }
        title to section.items
    }
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
): String = setsSummaryParts(machine, sets, preferred).joinToString(" ")

/** [setsSummary] as its weights, when any count, then its reps, so a narrow row may wrap them. */
fun setsSummaryParts(
    machine: Machine,
    sets: List<WorkoutSet>,
    preferred: PreferredWeightUnit,
): List<String> {
    val unit = sharedUnit(machine, preferred)
    val groups = pyramidGroups(sets).map { it.sets }
    val separator = if (groups.any { it.size > 1 }) " - " else "-"
    val weights = groups.map { group -> group.map { shownWeight(it.weight, machine, preferred) } }
    val reps = groups.map { group -> group.map { it.reps } }
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
        if (weights.flatten().all { it == 0.0 }) {
            null
        } else {
            sign + groupsText(weights.map { it.map(::formatNumber) }, separator) + unit + side
        }
    val repsPart =
        when {
            reps.distinct().size != 1 -> groupsText(reps, separator)
            reps.first().size == 1 -> "${reps.size}x${reps.first().single()}"
            reps.size == 1 -> reps.first().joinToString("+")
            else -> "${reps.size}x(${reps.first().joinToString("+")})"
        }
    return listOfNotNull(weightPart, repsPart)
}

/** A pyramid's values are joined with "+"; equal groups are written once. */
private fun groupsText(
    groups: List<List<Any>>,
    separator: String,
): String =
    (if (groups.distinct().size == 1) groups.take(1) else groups)
        .joinToString(separator) { it.joinToString("+") }
