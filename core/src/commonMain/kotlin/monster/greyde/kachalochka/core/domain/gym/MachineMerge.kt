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
    val removed: Machine,
    val photos: List<Photo> = emptyList(),
)

/**
 * Everything of [removed]'s that the owner holds moves to [kept]; [removed] is deleted. The moved
 * sets' weights change by [weightShift]. The links move with [mergedLinks].
 */
fun mergedMachines(
    kept: Machine,
    removed: Machine,
    removedSets: List<WorkoutSet>,
    now: Instant,
    removedPhotos: List<Photo> = emptyList(),
    weightShift: Double = 0.0,
): MergedRows =
    MergedRows(
        sets = shiftedSets(removedSets, weightShift, now).map { it.copy(machineId = kept.id) },
        removed = removed.copy(deleted = true, updatedAt = now),
        photos = removedPhotos.map { it.copy(machineId = kept.id, updatedAt = now) },
    )

/** The owner's links of [removed], moved to [kept] or deleted when [kept] has them already. */
fun mergedLinks(
    kept: MachineId,
    removed: MachineId,
    ownLinks: List<MachineLink>,
    now: Instant,
): List<MachineLink> {
    val linked =
        ownLinks
            .filter { it.machineId == kept && !it.deleted }
            .map { it.linkedMachineId }
            .toMutableSet()
    // Moved, a link into [kept] would link it to itself, which the server refuses.
    linked += kept
    return ownLinks
        .filter { it.machineId == removed && !it.deleted }
        .map { link ->
            if (linked.add(link.linkedMachineId)) {
                link.copy(machineId = kept, updatedAt = now)
            } else {
                link.copy(deleted = true, updatedAt = now)
            }
        }
}
