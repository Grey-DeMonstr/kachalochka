package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

/** The machine a merge keeps: the one used first; one never used counts as newest. */
fun olderMachine(
    edited: Machine,
    other: Machine,
    firstSetAt: (MachineId) -> Instant?,
): Machine {
    val otherFirst = firstSetAt(other.id) ?: return edited
    val editedFirst = firstSetAt(edited.id) ?: return other
    return if (otherFirst < editedFirst) other else edited
}

data class MergedRows(
    val sets: List<WorkoutSet>,
    val links: List<MachineLink>,
    val removed: Machine,
    val photos: List<Photo> = emptyList(),
)

/** Everything of [removed]'s that the owner holds moves to [kept]; [removed] is deleted. */
fun mergedMachines(
    kept: Machine,
    removed: Machine,
    removedSets: List<WorkoutSet>,
    ownLinks: List<MachineLink>,
    now: Instant,
    removedPhotos: List<Photo> = emptyList(),
): MergedRows {
    val linked =
        ownLinks
            .filter { it.machineId == kept.id && !it.deleted }
            .map { it.linkedMachineId }
            .toMutableSet()
    // Moved, a link into [kept] would link it to itself, which the server refuses.
    linked += kept.id
    val links =
        ownLinks
            .filter { it.machineId == removed.id && !it.deleted }
            .map { link ->
                if (linked.add(link.linkedMachineId)) {
                    link.copy(machineId = kept.id, updatedAt = now)
                } else {
                    link.copy(deleted = true, updatedAt = now)
                }
            }
    return MergedRows(
        sets = removedSets.map { it.copy(machineId = kept.id, updatedAt = now) },
        links = links,
        removed = removed.copy(deleted = true, updatedAt = now),
        photos = removedPhotos.map { it.copy(machineId = kept.id, updatedAt = now) },
    )
}
