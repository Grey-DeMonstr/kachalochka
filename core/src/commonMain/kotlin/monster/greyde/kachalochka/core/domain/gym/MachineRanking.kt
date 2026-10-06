package monster.greyde.kachalochka.core.domain.gym

data class MachineRanking(
    val offerCreate: Boolean,
    val machines: List<Machine>,
)

/**
 * The machines whose name, or one of the names it [alsoGoesBy], [nameMatches] [query] and that
 * carry every one of [tags], in [order].
 */
fun rankMachines(
    query: String,
    machines: List<Machine>,
    order: Comparator<Machine>,
    tags: Set<String> = emptySet(),
    alsoGoesBy: (Machine) -> List<String> = { emptyList() },
): MachineRanking {
    val needle = query.trim()
    val live = machines.filterNot { it.deleted }.sortedWith(order)
    val exists = live.any { it.name.trim().equals(needle, ignoreCase = true) }
    return MachineRanking(
        offerCreate = needle.isNotEmpty() && !exists,
        machines =
            live.filter {
                anyNameMatches(listOf(it.name) + alsoGoesBy(it), needle) &&
                    it.tags.containsAll(tags)
            },
    )
}
