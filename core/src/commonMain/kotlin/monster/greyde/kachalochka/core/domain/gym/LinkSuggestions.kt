package monster.greyde.kachalochka.core.domain.gym

/**
 * The other own machines each of the account's [own] machines could be merged with, for those
 * that have any: of the same name, or the same machine through [links], every visible link.
 */
fun linkSuggestions(
    own: List<Machine>,
    links: List<MachineLink>,
): Map<MachineId, List<Machine>> {
    val clusters = MachineClusters(links)
    val byName = compareBy<Machine> { it.name.lowercase() }.thenBy { it.id.value }
    return own
        .associate { machine ->
            val cluster = clusters.of(machine.id)
            val merges =
                own.filter {
                    it.id != machine.id && (it.id in cluster || it.name.sameName(machine.name))
                }
            machine.id to merges.sortedWith(byName)
        }.filterValues { it.isNotEmpty() }
}

private fun String.sameName(other: String): Boolean =
    trim().equals(other.trim(), ignoreCase = true)
