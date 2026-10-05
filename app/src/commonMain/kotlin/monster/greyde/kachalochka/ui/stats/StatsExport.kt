package monster.greyde.kachalochka.ui.stats

import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.ui.strings.AppStrings

/** The overall [sections] as a message: "ГДМ, за месяц", then each card's change and best set. */
internal fun statsExportText(
    nickname: String,
    period: StatsPeriod,
    sections: List<StatsSectionUi<ProgressCardUi>>,
): String {
    val header =
        listOf(nickname.trim(), AppStrings.current.forPeriod(period.months))
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    val body =
        sections.joinToString("\n\n") { section ->
            (listOfNotNull(section.title) + section.items.map(::exportLine)).joinToString("\n")
        }
    return "$header\n\n$body"
}

private fun exportLine(card: ProgressCardUi): String {
    val newMachine = card.change.replaceFirstChar { it.lowercase() }
    val change = if (card.newMachine) "— $newMachine" else card.change
    return "${card.name} $change (${card.to})"
}
