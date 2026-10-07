package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.friendMachineGroups
import monster.greyde.kachalochka.core.domain.friends.friendMachineRows
import monster.greyde.kachalochka.core.domain.friends.linkedFriendMachines
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.coverPhoto
import monster.greyde.kachalochka.core.domain.gym.linkSuggestions
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.account.accountAvatar
import kotlin.time.Clock

/** The account's live machines, with the photos and links that place them. */
data class OwnMachines(
    val owner: UserId?,
    val machines: List<Machine>,
    val photos: List<Photo>,
    val links: List<MachineLink>,
    /** The signed-in account as its friends see it. */
    val me: Friend? = null,
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

    /** Every friend's machine in a cluster that holds none of [own], with its cluster. */
    fun offeredGroups(own: List<Machine>): List<List<FriendMachine>> =
        friendMachineGroups(friends, own, clusters, links)

    fun suggestions(own: List<Machine>): Map<MachineId, List<Machine>> =
        linkSuggestions(own, friends.map { it.machine }, links)

    /** The friends' machines suggested for [machine] to link to directly. */
    fun suggestedLinks(
        machine: MachineId,
        own: List<Machine>,
    ): List<FriendMachine> {
        val byId = friends.associateBy { it.machine.id }
        return suggestions(own)[machine].orEmpty().mapNotNull { byId[it.id] }
    }
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

    /** Offline, only the account's own links and machine names suggest anything. */
    val suggestions: Map<MachineId, List<Machine>> =
        this.group?.suggestions(own.machines)
            ?: linkSuggestions(own.machines, emptyList(), own.links)

    /** The friends' machines [machine] is linked with, read online. */
    fun linked(machine: MachineId): List<FriendMachine> =
        group?.let { linkedFriendMachines(machine, it.friends, it.clusters) }.orEmpty()

    /** The names of the friends' machines [machine] is linked with, for a search. */
    fun linkedNames(machine: Machine): List<String> = linked(machine.id).map { it.machine.name }

    /** The machines [machine] is linked with: the account's own, then the friends' by owner. */
    fun linkedWith(machine: MachineId): List<LinkedMachineUi> {
        val cluster = clusters.of(machine)
        val mine =
            own.me
                ?.let { me ->
                    own.machines
                        .filter { it.id != machine && it.id in cluster }
                        .sortedBy { it.name.lowercase() }
                        .map { LinkedMachineUi(it.id, me, it.name, own = true) }
                }.orEmpty()
        val theirs =
            linked(machine)
                .filter { it.machine.id != machine }
                .map { LinkedMachineUi(it.machine.id, it.owner, it.machine.name) }
        return mine + theirs
    }

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
    private val accounts: Accounts,
    private val avatars: AccountAvatars,
) {
    suspend fun own(owner: UserId?): OwnMachines =
        OwnMachines(owner, machines.all(owner), photos.all(owner), links.all(owner), me(owner))

    private fun me(owner: UserId?): Friend? {
        val account = accounts.accounts.value.firstOrNull { it.userId == owner } ?: return null
        val avatar = accountAvatar(account, avatars.photos.value)
        return Friend(account.userId, account.displayName, avatar)
    }

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
