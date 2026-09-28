package monster.greyde.kachalochka.core.domain.friends

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.visitOrder

/** The visit holding each friend's newest set, newest first, for at most [limit] friends. */
fun latestVisitsByMember(
    sets: List<WorkoutSet>,
    limit: Int,
): List<VisitId> =
    sets
        .filterNot { it.deleted }
        .sortedByDescending { it.recordedAt }
        .distinctBy { it.userId }
        .take(limit)
        .map { it.visitId }

/** [machines] holds the friends' machines the [sets] were recorded on. */
fun friendResults(
    friends: List<Friend>,
    visits: List<VisitId>,
    sets: List<WorkoutSet>,
    machines: List<Machine>,
): List<FriendResult> {
    val byId = friends.associateBy { it.userId }
    val machinesById = machines.associateBy { it.id }
    return visits.mapNotNull { visit ->
        val visitSets = sets.filter { it.visitId == visit && !it.deleted }.sortedWith(visitOrder)
        val first = visitSets.firstOrNull() ?: return@mapNotNull null
        val friend = first.userId?.let(byId::get) ?: return@mapNotNull null
        val machine = machinesById[first.machineId] ?: return@mapNotNull null
        FriendResult(friend, machine, visitSets)
    }
}

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

/** One row per friends' cluster without an own machine: the original-most machine. */
fun friendMachineRows(
    friendMachines: List<FriendMachine>,
    own: List<Machine>,
    clusters: MachineClusters,
    links: List<MachineLink>,
): List<FriendMachine> {
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
        .map { cluster -> cluster.minWith(originalFirst) }
        .sortedBy { it.machine.name.lowercase() }
}
