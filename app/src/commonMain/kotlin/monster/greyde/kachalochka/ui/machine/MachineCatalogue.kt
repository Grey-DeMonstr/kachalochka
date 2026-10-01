package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.friendMachineRows
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.coverPhoto
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock

/** The account's live machines, with the photos and links that place them. */
data class OwnMachines(
    val owner: UserId?,
    val machines: List<Machine>,
    val photos: List<Photo>,
    val links: List<MachineLink>,
)

/** Group mates' live machines, links and photos, as read online for [owner]. */
data class GroupMachines(
    val owner: UserId,
    val friends: List<FriendMachine>,
    /** The owner's links and the group mates'. */
    val links: List<MachineLink>,
    val photos: List<Photo>,
) {
    val clusters: MachineClusters = MachineClusters(links)

    /** One friend's machine per cluster that holds none of [own]. */
    fun offered(own: List<Machine>): List<FriendMachine> =
        friendMachineRows(friends, own, clusters, links)
}

/** What the account sees: its own machines, and the group's once a read for it has landed. */
class ShownMachines(
    val own: OwnMachines,
    group: GroupMachines?,
) {
    /** A read for another account, left from before a switch, is never shown. */
    val group: GroupMachines? = group?.takeIf { it.owner == own.owner }

    val clusters: MachineClusters = this.group?.clusters ?: MachineClusters(own.links)

    val offered: List<FriendMachine> = this.group?.offered(own.machines).orEmpty()

    /** An own machine's chosen cover counts, and a friend's machine's as its owner chose it. */
    fun cover(machine: MachineId): Photo? {
        val chosen =
            own.machines.firstOrNull { it.id == machine }?.coverPhoto
                ?: group
                    ?.friends
                    ?.firstOrNull { it.machine.id == machine }
                    ?.machine
                    ?.coverPhoto
        return coverPhoto(machine, own.photos + group?.photos.orEmpty(), clusters, chosen)
    }
}

/**
 * The machines an account sees: its own, read from the device, and its group mates', read online
 * and joined to them by links (spec §4.5).
 */
class MachineCatalogue(
    private val machines: MachineRepository,
    private val photos: PhotoRepository,
    private val links: MachineLinkRepository,
    private val friends: FriendsRepository,
    private val clock: Clock,
    private val sync: SyncTrigger,
) {
    suspend fun own(owner: UserId?): OwnMachines =
        OwnMachines(owner, machines.all(owner), photos.all(owner), links.all(owner))

    /** Machines joined by the owner's live links and the group mates', the latter read online. */
    suspend fun clusters(owner: UserId): MachineClusters = MachineClusters(visibleLinks(owner))

    /** Fails when the network does not answer. */
    suspend fun group(owner: UserId): GroupMachines =
        coroutineScope {
            val mates = async { friends.groupMachines(owner) }
            val all = async { visibleLinks(owner) }
            val pictures = async { friends.groupPhotos(owner) }
            GroupMachines(owner, mates.await(), all.await(), pictures.await())
        }

    /** [friend]'s machine becomes [owner]'s own, linked to it; the copy is returned. */
    suspend fun take(
        owner: UserId,
        friend: Machine,
    ): Machine {
        val (copy, link) = linkedCopy(friend, owner, clock.now())
        machines.upsert(copy)
        links.upsert(link)
        sync.request()
        return copy
    }

    private suspend fun visibleLinks(owner: UserId): List<MachineLink> =
        links.all(owner) + friends.groupLinks(owner)
}
