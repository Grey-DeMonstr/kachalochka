package monster.greyde.kachalochka.core.domain.gym

/**
 * What each of the account's [own] machines could still be joined with, for those that have any:
 * first the other own machines to merge with — of the same name, or the same machine through
 * links — then, for each friend with machines in its cluster but none linked to it directly, one
 * of those machines to link to. [friends] are the group mates' live machines; [links] are every
 * visible link.
 */
fun linkSuggestions(
    own: List<Machine>,
    friends: List<Machine>,
    links: List<MachineLink>,
): Map<MachineId, List<Machine>> {
    val live = links.filterNot { it.deleted }
    val clusters = MachineClusters(live)
    val byName = compareBy<Machine> { it.name.lowercase() }.thenBy { it.id.value }
    return own
        .associate { machine ->
            val cluster = clusters.of(machine.id)
            val direct =
                live
                    .filter { it.machineId == machine.id || it.linkedMachineId == machine.id }
                    .flatMap { listOf(it.machineId, it.linkedMachineId) }
                    .toSet()
            val merges =
                own.filter {
                    it.id != machine.id && (it.id in cluster || it.name.sameName(machine.name))
                }
            val links =
                friends
                    .filter { it.id in cluster }
                    .groupBy { it.userId }
                    .values
                    .filter { theirs -> theirs.none { it.id in direct } }
                    .map { theirs -> theirs.minWith(byName) }
            machine.id to merges.sortedWith(byName) + links.sortedWith(byName)
        }.filterValues { it.isNotEmpty() }
}

private fun String.sameName(other: String): Boolean =
    trim().equals(other.trim(), ignoreCase = true)
