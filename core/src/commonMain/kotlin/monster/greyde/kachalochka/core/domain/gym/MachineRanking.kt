package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

data class MachineRanking(
    val offerCreate: Boolean,
    val machines: List<Machine>,
)

fun rankMachines(
    query: String,
    machines: List<Machine>,
    lastUsed: Map<MachineId, Instant>,
): MachineRanking {
    val needle = query.trim()
    val byRecency =
        machines
            .filterNot { it.deleted }
            .sortedWith(
                compareByDescending<Machine> { lastUsed[it.id] }.thenBy { it.name.lowercase() },
            )
    val matching = byRecency.filter { it.name.contains(needle, ignoreCase = true) }
    val exists = byRecency.any { it.name.trim().equals(needle, ignoreCase = true) }
    return MachineRanking(
        offerCreate = needle.isNotEmpty() && !exists,
        machines = matching.ifEmpty { byRecency },
    )
}
