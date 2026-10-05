package monster.greyde.kachalochka.core.domain.gym

import kotlin.time.Instant

/**
 * Imported history makes duplicates whose sets are older, so the machine used last is the one in
 * use; one never used counts as oldest.
 */
fun suggestedToKeep(
    edited: Machine,
    other: Machine,
    lastSetAt: (MachineId) -> Instant?,
): Machine {
    val otherLast = lastSetAt(other.id) ?: return edited
    val editedLast = lastSetAt(edited.id) ?: return other
    return if (otherLast > editedLast) other else edited
}

data class MergedRows(
    val sets: List<WorkoutSet>,
    val links: List<MachineLink>,
    val removed: Machine,
    val photos: List<Photo> = emptyList(),
)

/**
 * Everything of [removed]'s that the owner holds moves to [kept]; [removed] is deleted. The moved
 * sets' weights change by [weightShift].
 */
fun mergedMachines(
    kept: Machine,
    removed: Machine,
    removedSets: List<WorkoutSet>,
    ownLinks: List<MachineLink>,
    now: Instant,
    removedPhotos: List<Photo> = emptyList(),
    weightShift: Double = 0.0,
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
        sets = shiftedSets(removedSets, weightShift, now).map { it.copy(machineId = kept.id) },
        links = links,
        removed = removed.copy(deleted = true, updatedAt = now),
        photos = removedPhotos.map { it.copy(machineId = kept.id, updatedAt = now) },
    )
}
