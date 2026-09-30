package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

data class MachineRanking(
    val offerCreate: Boolean,
    val machines: List<Machine>,
)

/** The machines whose name holds [query] and that carry every one of [tags], most recent first. */
fun rankMachines(
    query: String,
    machines: List<Machine>,
    lastUsed: Map<MachineId, Instant>,
    tags: Set<String> = emptySet(),
): MachineRanking {
    val needle = query.trim()
    val byRecency =
        machines
            .filterNot { it.deleted }
            .sortedWith(
                compareByDescending<Machine> { lastUsed[it.id] }.thenBy { it.name.lowercase() },
            )
    val exists = byRecency.any { it.name.trim().equals(needle, ignoreCase = true) }
    return MachineRanking(
        offerCreate = needle.isNotEmpty() && !exists,
        machines =
            byRecency.filter {
                it.name.contains(needle, ignoreCase = true) && it.tags.containsAll(tags)
            },
    )
}
