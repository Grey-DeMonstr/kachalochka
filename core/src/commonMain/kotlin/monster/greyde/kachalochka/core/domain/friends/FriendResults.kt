package monster.greyde.kachalochka.core.domain.friends

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
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

fun friendResults(
    friends: List<Friend>,
    visits: List<VisitId>,
    sets: List<WorkoutSet>,
): List<FriendResult> {
    val byId = friends.associateBy { it.userId }
    return visits.mapNotNull { visit ->
        val visitSets = sets.filter { it.visitId == visit && !it.deleted }.sortedWith(visitOrder)
        val friend = visitSets.firstOrNull()?.userId?.let(byId::get) ?: return@mapNotNull null
        FriendResult(friend, visitSets)
    }
}

/** Each of [theirs] by the name of the viewer's machine sharing its key, or by its own. */
fun namesForViewer(
    theirs: List<Machine>,
    mine: List<Machine>,
): Map<MachineId, String> {
    val byKey = mine.associate { it.linkKey to it.name }
    return theirs.associate { it.id to (byKey[it.linkKey] ?: it.name) }
}
