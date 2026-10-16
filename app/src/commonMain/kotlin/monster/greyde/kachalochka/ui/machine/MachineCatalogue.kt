package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupGym
import monster.greyde.kachalochka.core.domain.friends.friendMachineGroups
import monster.greyde.kachalochka.core.domain.friends.friendMachineRows
import monster.greyde.kachalochka.core.domain.friends.linkedFriendMachines
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.coverPhoto
import monster.greyde.kachalochka.core.domain.gym.linkSuggestions
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.gym.mergedLinks
import monster.greyde.kachalochka.core.domain.gym.missingDirectLinks
import monster.greyde.kachalochka.core.domain.gym.ownStandIn
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.account.accountAvatar
import monster.greyde.kachalochka.ui.friends.reading
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

    /** The own machines each could be merged with; offline, only own links join them. */
    val suggestions: Map<MachineId, List<Machine>> =
        linkSuggestions(own.machines, this.group?.links ?: own.links)

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

    /** The own machines of [machine]'s cluster, then the friends' it is linked with. */
    fun linkedRows(machine: MachineId): List<Machine> {
        val cluster = clusters.of(machine)
        val mine = own.machines.filter { it.id != machine && it.id in cluster }
        return mine + linked(machine).map { it.machine }.filter { it.id != machine }
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
    private val failures: FailureLog,
) {
    private val linkWrites = Mutex()
    private val flights = Mutex()
    private val inFlight = mutableMapOf<UserId, CompletableDeferred<GroupMachines>>()
    private val lastGyms = MutableStateFlow<Map<UserId, GroupGym>>(emptyMap())

    suspend fun own(owner: UserId?): OwnMachines =
        OwnMachines(owner, machines.all(owner), photos.all(owner), links.all(owner), me(owner))

    private fun me(owner: UserId?): Friend? {
        val account = accounts.accounts.value.firstOrNull { it.userId == owner } ?: return null
        val avatar = accountAvatar(account, avatars.photos.value)
        return Friend(account.userId, account.shownName, avatar)
    }

    /** Machines joined by the owner's live links and the group mates', the latter read online. */
    suspend fun clusters(owner: UserId): MachineClusters = MachineClusters(visibleLinks(owner))

    /**
     * Fails when the network does not answer; once it has, [owner]'s links are kept direct. Reads
     * for one owner at the same time share one; when the caller running it is cancelled, the
     * others read again.
     */
    suspend fun group(owner: UserId): GroupMachines {
        var leads = false
        val shared =
            flights.withLock {
                inFlight.getOrPut(owner) {
                    CompletableDeferred<GroupMachines>().also { leads = true }
                }
            }
        if (!leads) {
            return try {
                shared.await()
            } catch (stopped: CancellationException) {
                currentCoroutineContext().ensureActive()
                group(owner)
            }
        }
        val result = runCatching { readGroup(owner) }
        withContext(NonCancellable) { flights.withLock { inFlight.remove(owner) } }
        shared.completeWith(result)
        return result.getOrThrow()
    }

    private suspend fun readGroup(owner: UserId): GroupMachines =
        coroutineScope {
            val mine = async { links.all(owner) }
            val gym = friends.groupGym(owner)
            lastGyms.update { it + (owner to gym) }
            val all =
                writingLinks {
                    keepDirect(
                        owner,
                        gym.machines,
                        gym.links,
                        mine.await() + gym.links,
                    )
                }
            GroupMachines(owner, gym.machines, all, gym.photos)
        }

    /** [owner]'s last group read, joined to the own links as they are now; null before one. */
    suspend fun lastGroup(owner: UserId): GroupMachines? {
        val gym = lastGyms.value[owner] ?: return null
        return GroupMachines(owner, gym.machines, links.all(owner) + gym.links, gym.photos)
    }

    private fun forget(owner: UserId) = lastGyms.update { it - owner }

    /**
     * Friends' links into [removed] move to [kept] on the server; false when it does not answer.
     * [owner]'s last group read is dropped, since friends' rows changed.
     */
    suspend fun repointFriends(
        owner: UserId,
        removed: MachineId,
        kept: MachineId,
    ): Boolean =
        reading {
            if (friends.groupLinks(owner).any { it.linkedMachineId == removed }) {
                friends.repointLinks(removed, kept)
                forget(owner)
            }
        }.isSuccess

    /**
     * [owner]'s links of [removed] move to [kept]. Requests no sync, since the merge requests one
     * after its last row.
     */
    suspend fun moveLinks(
        owner: UserId?,
        removed: MachineId,
        kept: MachineId,
    ) = writingLinks(sync = false) {
        mergedLinks(kept, removed, links.all(owner), clock.now()).forEach { upsert(it) }
    }

    /**
     * Friends' links into [machine] can be broken only on the server, so they go first, and
     * nothing is written and false is returned when it does not answer. A sync always follows.
     */
    suspend fun unlink(
        owner: UserId?,
        machine: MachineId,
    ): Boolean {
        if (owner != null) {
            if (reading { friends.breakLinks(machine) }.isFailure) return false
            forget(owner)
        }
        writingLinks {
            syncAfter = true
            val now = clock.now()
            links
                .all(owner)
                .filter { it.machineId == machine || it.linkedMachineId == machine }
                .forEach { upsert(it.copy(deleted = true, updatedAt = now)) }
        }
        return true
    }

    /**
     * [friend]'s machine becomes [owner]'s own, linked to it and to the rest of its cluster in
     * [group]; the copy is returned.
     */
    suspend fun take(
        owner: UserId,
        friend: Machine,
        group: GroupMachines,
    ): Machine = writingLinks { copyOf(owner, friend, group) }

    /**
     * [owner]'s machine of [friend]'s cluster, else a new copy; null when [group] lacks [friend].
     * The own links are read again, so a copy written after [group] counts.
     */
    suspend fun friendStandIn(
        owner: UserId,
        friend: MachineId,
        group: GroupMachines,
    ): Machine? {
        val shown = group.friends.firstOrNull { it.machine.id == friend }?.machine ?: return null
        return writingLinks {
            val cluster = MachineClusters(group.links + links.all(owner)).of(friend)
            machines.all(owner).firstOrNull { it.id in cluster } ?: copyOf(owner, shown, group)
        }
    }

    /** [machine], [owner]'s own, links to [friend] and to the rest of its cluster in [group]. */
    suspend fun link(
        owner: UserId,
        machine: MachineId,
        friend: MachineId,
        group: GroupMachines?,
    ) {
        val link = MachineLink(MachineLinkId.random(), owner, machine, friend, clock.now(), false)
        writingLinks {
            keepDirect(
                owner,
                group?.friends.orEmpty(),
                group?.mateLinks(owner).orEmpty(),
                group?.links.orEmpty(),
                listOf(link),
            )
        }
    }

    /**
     * The owner's machine standing for [shown] without writing anything: [shown] itself when it
     * is the owner's, else its [ownStandIn] in the cluster the two accounts' own links show.
     */
    suspend fun deviceStandIn(
        owner: UserId?,
        shown: Machine,
    ): Machine? {
        if (shown.userId == owner) return shown
        val known = links.all(owner) + links.all(shown.userId)
        return ownStandIn(shown, machines.all(owner), MachineClusters(known))
    }

    /**
     * The owner's own machine standing for [shown], which may be another account's: its
     * [deviceStandIn], else a new copy linked to [shown] and to the rest of its cluster (spec
     * §4.5).
     */
    suspend fun ownCopy(
        owner: UserId?,
        shown: Machine,
    ): Machine =
        writingLinks {
            deviceStandIn(owner, shown) ?: run {
                val known = links.all(owner) + links.all(shown.userId)
                val now = clock.now()
                val (copy, link) = linkedCopy(shown, owner, now)
                machines.upsert(copy)
                upsert(link)
                val cluster = MachineClusters(known).of(shown.id)
                missingDirectLinks(owner, listOf(copy.id), cluster, known + link, now)
                    .forEach { upsert(it) }
                copy
            }
        }

    /**
     * Runs [write] with every other link write waiting. Each write reads the own links again under
     * the lock, so two writes at once never write the same link. Not re-entrant. Asks for a sync
     * when [write] wrote, unless [sync] is false.
     */
    private suspend fun <T> writingLinks(
        sync: Boolean = true,
        write: suspend LinkWriter.() -> T,
    ): T =
        linkWrites.withLock {
            val writer = LinkWriter()
            try {
                writer.write()
            } finally {
                if (sync && writer.syncAfter) this@MachineCatalogue.sync.request()
            }
        }

    private inner class LinkWriter {
        // A write stopped halfway may have reached the outbox, so it still asks for a pass.
        var syncAfter = false

        suspend fun upsert(link: MachineLink) {
            syncAfter = true
            links.upsert(link)
        }
    }

    private suspend fun LinkWriter.copyOf(
        owner: UserId,
        friend: Machine,
        group: GroupMachines,
    ): Machine {
        val (copy, link) = linkedCopy(friend, owner, clock.now())
        machines.upsert(copy)
        keepDirect(owner, group.friends, group.mateLinks(owner), group.links, listOf(link))
        return copy
    }

    /**
     * Writes [written], then the links each own machine misses to the friends' machines of its
     * cluster, and returns every link visible after. A failed upkeep is only recorded, since
     * [written] already stands: [before] and [written] are then returned.
     */
    private suspend fun LinkWriter.keepDirect(
        owner: UserId,
        mates: List<FriendMachine>,
        theirs: List<MachineLink>,
        before: List<MachineLink>,
        written: List<MachineLink> = emptyList(),
    ): List<MachineLink> {
        written.forEach { upsert(it) }
        return try {
            val visible = links.all(owner) + theirs
            val missing =
                missingDirectLinks(
                    owner,
                    machines.all(owner).map { it.id },
                    mates.map { it.machine.id },
                    visible,
                    clock.now(),
                )
            missing.forEach { upsert(it) }
            visible + missing
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failed: Exception) {
            failures.record(failed)
            before + written
        }
    }

    private suspend fun visibleLinks(owner: UserId): List<MachineLink> =
        links.all(owner) + friends.groupLinks(owner)
}

private fun GroupMachines.mateLinks(owner: UserId): List<MachineLink> =
    links.filter { it.userId != owner }
