package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        coroutineScope {
            val machines = async { friends.groupMachines(owner) }
            val links = async { visibleLinks(owner, friends, ownLinks) }
            val all = links.await()
            GroupMachines(machines.await(), all, MachineClusters(all))
        }
    }.getOrNull()

/** [owner]'s live links and their group mates', the latter read online. */
suspend fun visibleLinks(
    owner: UserId,
    friends: FriendsRepository,
    ownLinks: MachineLinkRepository,
): List<MachineLink> = ownLinks.all(owner) + friends.groupLinks(owner)
