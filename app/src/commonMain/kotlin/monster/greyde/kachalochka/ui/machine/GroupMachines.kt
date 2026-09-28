package monster.greyde.kachalochka.ui.machine

import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.friends.reading

data class GroupMachines(
    val friends: List<FriendMachine>,
    val links: List<MachineLink>,
    val clusters: MachineClusters,
)

/** Friends' machines and every visible link, or null when the network does not answer. */
suspend fun loadGroupMachines(
    owner: UserId,
    friends: FriendsRepository,
    ownLinks: MachineLinkRepository,
): GroupMachines? =
    reading {
        val machines = friends.groupMachines(owner)
        val links = visibleLinks(owner, friends, ownLinks)
        GroupMachines(machines, links, MachineClusters(links))
    }.getOrNull()

/** [owner]'s live links and their group mates', the latter read online. */
suspend fun visibleLinks(
    owner: UserId,
    friends: FriendsRepository,
    ownLinks: MachineLinkRepository,
): List<MachineLink> = ownLinks.all(owner) + friends.groupLinks(owner)
