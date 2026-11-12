package monster.greyde.kachalochka.core.domain.friends

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks

/** Each of [theirs] by the name of the viewer's machine in its cluster, or by its own. */
fun namesForViewer(
    theirs: List<Machine>,
    mine: List<Machine>,
    clusters: MachineClusters,
): Map<MachineId, String> =
    theirs.associate { machine ->
        val own = mine.firstOrNull { clusters.sameMachine(it.id, machine.id) }
        machine.id to (own ?: machine).name
    }

/** The friends' machines in [machine]'s cluster, by owner name, then machine name. */
fun linkedFriendMachines(
    machine: MachineId,
    friendMachines: List<FriendMachine>,
    clusters: MachineClusters,
): List<FriendMachine> {
    val cluster = clusters.of(machine)
    return friendMachines
        .filter { it.machine.id in cluster }
        .sortedWith(
            compareBy<FriendMachine> { it.owner.displayName.lowercase() }
                .thenBy { it.machine.name.lowercase() },
        )
}

/**
 * The record of each friends' machine in [machine]'s cluster that has sets, in the order of
 * [linkedFriendMachines].
 */
fun friendRecords(
    machine: MachineId,
    friendMachines: List<FriendMachine>,
    clusters: MachineClusters,
    peaks: List<MachinePeaks>,
): List<FriendRecord> {
    val byMachine = peaks.associateBy { it.machineId }
    return linkedFriendMachines(machine, friendMachines, clusters).mapNotNull {
        byMachine[it.machine.id]?.let { found ->
            FriendRecord(it.owner, it.machine, found.best(it.machine.weightMode))
        }
    }
}

/** One row per friends' cluster without an own machine: the original-most machine. */
fun friendMachineRows(
    friendMachines: List<FriendMachine>,
    own: List<Machine>,
    clusters: MachineClusters,
    links: List<MachineLink>,
): List<FriendMachine> =
    friendMachineGroups(friendMachines, own, clusters, links).map {
        it.first()
    }

/**
 * The friends' clusters without an own machine, each original-most machine first, ordered by the
 * name of that machine.
 */
fun friendMachineGroups(
    friendMachines: List<FriendMachine>,
    own: List<Machine>,
    clusters: MachineClusters,
    links: List<MachineLink>,
): List<List<FriendMachine>> {
    val ownClusters = own.flatMap { clusters.of(it.id) }.toSet()
    val outgoing = links.filterNot { it.deleted }.groupingBy { it.machineId }.eachCount()
    val originalFirst =
        compareBy<FriendMachine> { outgoing[it.machine.id] ?: 0 }
            .thenBy { it.owner.displayName }
            .thenBy { it.machine.id.value }
    return friendMachines
        .filter { it.machine.id !in ownClusters }
        .groupBy { clusters.of(it.machine.id) }
        .values
        .map { cluster -> cluster.sortedWith(originalFirst) }
        .sortedBy {
            it
                .first()
                .machine.name
                .lowercase()
        }
}
