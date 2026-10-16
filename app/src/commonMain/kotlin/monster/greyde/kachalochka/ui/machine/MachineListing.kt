package monster.greyde.kachalochka.ui.machine

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.hasAllTags
import monster.greyde.kachalochka.core.domain.gym.hasTag
import monster.greyde.kachalochka.core.domain.gym.machineOrder
import monster.greyde.kachalochka.core.domain.gym.nameMatches
import monster.greyde.kachalochka.core.domain.gym.rankMachines
import monster.greyde.kachalochka.core.domain.gym.sortedTags
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import kotlin.time.Duration

data class TagChoiceUi(
    val name: String,
    val chosen: Boolean,
)

/** The chips of the tags [machines] hold and of the [chosen] ones, [chosen] marked. */
internal fun tagChoices(
    machines: List<Machine>,
    chosen: Set<String>,
): List<TagChoiceUi> {
    val tags = sortedTags(machines.flatMap { it.tags } + chosen)
    return tags.map { TagChoiceUi(it, hasTag(chosen, it)) }
}

/**
 * Of [chosen], the tags [shown]'s machines still hold: a tag no machine holds is deleted. Without
 * a group read, a tag the own machines lack may still be a friend's, so it stays.
 */
internal fun heldTags(
    chosen: Set<String>,
    shown: ShownMachines,
): Set<String> {
    val held = shown.own.machines.flatMap { it.tags }
    val friendsKnown = shown.group != null || shown.own.owner == null
    val friends = shown.offered.flatMap { it.machine.tags }
    return chosen.filterTo(mutableSetOf()) {
        hasTag(held, it) || !friendsKnown || hasTag(friends, it)
    }
}

/** What the user asked a machine list to show. */
data class MachineQuery(
    val text: String = "",
    val tags: Set<String> = emptySet(),
    /** Own machines the visit already holds, found apart from the rest. */
    val inVisit: Set<MachineId> = emptySet(),
    /** Only own machines with a merge suggestion, and no friends' ones. */
    val onlySuggested: Boolean = false,
)

data class MachineListing(
    /** [MachineQuery] as it still applies: held tags only, the chip only with suggestions. */
    val query: MachineQuery,
    val own: List<MachineCardUi>,
    /** Marked `inVisit = true`. */
    val inVisit: List<MachineCardUi>,
    val friendSections: List<FriendSectionUi>,
    val tags: List<TagChoiceUi>,
    val hasSuggestions: Boolean,
    /** Some text is typed and no own machine has that name. */
    val offerCreate: Boolean,
    val nothingFound: Boolean,
)

/** The machines [query] finds in what this read shows, as cards in the [preferred] unit. */
internal fun MachineRead.listing(
    query: MachineQuery,
    sort: MachineSort,
    preferred: PreferredWeightUnit,
    today: CalendarDay,
    offset: Duration,
): MachineListing {
    val held = heldTags(query.tags, shown)
    val suggested = shown.suggestions
    val only = query.onlySuggested && suggested.isNotEmpty()
    val ranking =
        rankMachines(
            query.text,
            shown.own.machines,
            machineOrder(sort, ownPeaks.associateBy { it.machineId }),
            held,
            shown::linkedNames,
        )
    val found = ranking.machines.filter { !only || it.id in suggested }
    val (alreadyIn, notIn) = found.partition { it.id in query.inVisit }
    val cards = MachineCards(shown, ownPeaks, friendPeaks, preferred, today, offset)
    val own = notIn.map(cards::own)
    val inVisit = alreadyIn.map { cards.own(it).copy(inVisit = true) }
    val friends =
        shown.offered.filter {
            !only && nameMatches(it.machine.name, query.text) && hasAllTags(it.machine.tags, held)
        }
    val friendOrder = machineOrder(sort, friendPeaks.associateBy { it.machineId })
    val sections = cards.friendSections(friends, colors, friendOrder)
    val filtering = query.text.isNotBlank() || held.isNotEmpty() || only
    return MachineListing(
        query = query.copy(tags = held, onlySuggested = only),
        own = own,
        inVisit = inVisit,
        friendSections = sections,
        tags = tagChoices(shown.own.machines + shown.offered.map { it.machine }, held),
        hasSuggestions = suggested.isNotEmpty(),
        offerCreate = ranking.offerCreate,
        nothingFound = filtering && own.isEmpty() && inVisit.isEmpty() && sections.isEmpty(),
    )
}
