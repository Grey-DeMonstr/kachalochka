package monster.greyde.kachalochka.ui.stats

import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.ui.strings.AppStrings

/** The overall [sections] as a message: "GDM, past month", then each card's change and best. */
internal fun statsExportText(
    nickname: String,
    period: StatsPeriod,
    sections: List<StatsSectionUi<ProgressCardUi>>,
): String {
    val body =
        sections.joinToString("\n\n") { section ->
            (listOfNotNull(section.title) + section.items.map(::exportLine)).joinToString("\n")
        }
    return "${statsHeader(nickname, period)}\n\n$body"
}

/** "GDM, past month": the line that opens both the text and the picture. */
internal fun statsHeader(
    nickname: String,
    period: StatsPeriod,
): String =
    listOf(nickname.trim(), AppStrings.current.forPeriod(period.months))
        .filter { it.isNotEmpty() }
        .joinToString(", ")

private fun exportLine(card: ProgressCardUi): String {
    val newMachine = card.change.replaceFirstChar { it.lowercase() }
    val change = if (card.newMachine) "— $newMachine" else card.change
    return "${card.name} $change (${card.to})"
}
