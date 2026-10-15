package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

/**
 * The links [owner] writes so that each of its [own] machines links directly to every machine of
 * [friends] in its cluster. [links] are every visible link, the owner's and the group mates'; a
 * link in either direction counts. Each new link takes its id from [newId].
 */
fun missingDirectLinks(
    owner: UserId?,
    own: Collection<MachineId>,
    friends: Collection<MachineId>,
    links: List<MachineLink>,
    now: Instant,
    newId: () -> MachineLinkId = MachineLinkId::random,
): List<MachineLink> {
    val live = links.filterNot { it.deleted }
    val clusters = MachineClusters(live)
    val direct = live.map { setOf(it.machineId, it.linkedMachineId) }.toSet()
    val ownIds = own.toSet()
    val targets = friends.filterNot { it in ownIds }.distinct().sortedBy { it.value }
    return ownIds
        .sortedBy { it.value }
        .flatMap { machine ->
            val cluster = clusters.of(machine)
            targets
                .filter { it in cluster && setOf(machine, it) !in direct }
                .map { MachineLink(newId(), owner, machine, it, now, false) }
        }
}
