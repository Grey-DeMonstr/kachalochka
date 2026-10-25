package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.data.SingleFlight
import monster.greyde.kachalochka.core.data.catching
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
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.convertedSets
import monster.greyde.kachalochka.core.domain.gym.coverPhoto
import monster.greyde.kachalochka.core.domain.gym.linkSuggestions
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.gym.machineOrder
import monster.greyde.kachalochka.core.domain.gym.mergedLinks
import monster.greyde.kachalochka.core.domain.gym.mergedMachines
import monster.greyde.kachalochka.core.domain.gym.missingDirectLinks
import monster.greyde.kachalochka.core.domain.gym.ownStandIn
import monster.greyde.kachalochka.core.domain.gym.platformShift
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
                        .sortedWith(machineOrder)
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

/** How a merge of two own machines ended; see [MachineCatalogue.merge]. */
enum class MergeOutcome { Done, Offline, Gone }

/**
 * A machine a screen shows: the account's own, or a group mate's when [ofFriend]. [group] is the
 * group read the screen holds, used instead of a new one when it was made for the active account.
 */
data class PlacedMachine(
    val id: MachineId,
    val ofFriend: Boolean = false,
    val group: GroupMachines? = null,
)

/** The account's machine for a shown one; see [MachineCatalogue.standIn]. */
sealed interface StandIn {
    data class Found(
        val machine: Machine,
    ) : StandIn

    data object Offline : StandIn

    /** A friend's machine the friend no longer shares. */
    data object Gone : StandIn

    /** Another account became active; nothing was written. */
    data object Switched : StandIn
}

/**
 * The machines an account sees: its own, read from the device, and its group mates', read online
 * and joined to them by links. The screens write machine links here, under one lock (spec §4.5).
 */
class MachineCatalogue(
    private val machines: MachineRepository,
    private val photos: PhotoRepository,
    private val links: MachineLinkRepository,
    private val sets: WorkoutSetRepository,
    private val friends: FriendsRepository,
    private val clock: Clock,
    private val sync: SyncTrigger,
    private val accounts: Accounts,
    private val avatars: AccountAvatars,
    private val failures: FailureLog,
) {
    private val linkWrites = Mutex()
    private val groupReads = SingleFlight<UserId, GroupMachines>()
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
    suspend fun group(owner: UserId): GroupMachines =
        groupReads.run(owner, { group(owner) }) { readGroup(owner) }

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
     * Merges [owner]'s [removed] into [kept]; the moved sets are converted to the kept unit when
     * [convert] and keep their totals under the kept platform when [adjust]. Friends' links into
     * [removed] can move only on the server, so signed in it goes first and nothing is written
     * when it does not answer. Once it has answered, the local rows follow even if the caller is
     * cancelled: sets, own links, photos, the machine last, then one sync request. Gone when a
     * machine is no longer [owner]'s live one or [owner] is no longer the active account.
     */
    suspend fun merge(
        owner: UserId?,
        kept: MachineId,
        removed: MachineId,
        convert: Boolean,
        adjust: Boolean,
    ): MergeOutcome {
        val keptRow = liveOwn(kept, owner)
        val removedRow = liveOwn(removed, owner)
        if (keptRow == null || removedRow == null || accounts.activeId.value != owner) {
            return MergeOutcome.Gone
        }
        if (owner != null && !repointFriends(owner, removed, kept)) return MergeOutcome.Offline
        withContext(NonCancellable) {
            val now = clock.now()
            val moved = sets.forMachine(removed).filter { it.userId == owner }
            val rows =
                mergedMachines(
                    keptRow,
                    removedRow,
                    if (convert) {
                        convertedSets(
                            moved,
                            removedRow.unit,
                            keptRow.unit,
                            keptRow.weightStep,
                            now,
                        )
                    } else {
                        moved
                    },
                    now,
                    photos.forMachine(removed).filter { it.userId == owner },
                    if (adjust) platformShift(removedRow, keptRow) else 0.0,
                )
            rows.sets.forEach { sets.upsert(it) }
            writingLinks(requestSync = false) {
                mergedLinks(kept, removed, links.all(owner), now).forEach { upsert(it) }
            }
            rows.photos.forEach { photos.upsert(it) }
            machines.upsert(rows.removed)
            sync.request()
        }
        return MergeOutcome.Done
    }

    private suspend fun liveOwn(
        id: MachineId,
        owner: UserId?,
    ): Machine? = machines.byId(id)?.takeIf { !it.deleted && it.userId == owner }

    /** False when the server does not answer; a repoint drops the last group read. */
    private suspend fun repointFriends(
        owner: UserId,
        removed: MachineId,
        kept: MachineId,
    ): Boolean =
        catching {
            if (friends.groupLinks(owner).any { it.linkedMachineId == removed }) {
                friends.repointLinks(removed, kept)
                forget(owner)
            }
        }.isSuccess

    /**
     * Friends' links into [machine] can be broken only on the server, so they go first, and
     * nothing is written and false is returned when it does not answer. Then the owner's links
     * that touch [machine] are soft-deleted. A sync always follows.
     */
    suspend fun unlink(
        owner: UserId?,
        machine: MachineId,
    ): Boolean {
        if (owner != null) {
            if (catching { friends.breakLinks(machine) }.isFailure) return false
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
        return ownStandIn(shown, machines.all(owner), MachineClusters(knownLinks(owner, shown)))
    }

    private suspend fun knownLinks(
        owner: UserId?,
        shown: Machine,
    ): List<MachineLink> = links.all(owner) + links.all(shown.userId)

    /**
     * The owner's own machine standing for [shown], which may be another account's: its
     * [deviceStandIn], else a new copy linked to [shown] and to the rest of its cluster (spec
     * §4.5).
     */
    suspend fun ownCopy(
        owner: UserId?,
        shown: Machine,
    ): Machine {
        if (shown.userId == owner) return shown
        return writingLinks { deviceStandIn(owner, shown) ?: deviceCopy(owner, shown) }
    }

    /**
     * [owner]'s machine for [shown], which a screen offered to [owner]: the machine itself when
     * it is [owner]'s; another device account's as [ownCopy] gives it; a friend's as
     * [friendStandIn] gives it over the group read the screen holds when that was made for
     * [owner], else over a new one. The active account is checked before each read and write, so
     * a switch meanwhile writes nothing.
     */
    suspend fun standIn(
        owner: UserId?,
        shown: PlacedMachine,
    ): StandIn {
        if (!active(owner)) return StandIn.Switched
        val found =
            if (shown.ofFriend) {
                standInForFriend(owner ?: return StandIn.Gone, shown)
            } else {
                standInOnDevice(owner, shown.id)
            }
        return if (found is StandIn.Found && !active(owner)) StandIn.Switched else found
    }

    private suspend fun standInOnDevice(
        owner: UserId?,
        id: MachineId,
    ): StandIn {
        val stored = machines.byId(id) ?: return StandIn.Gone
        if (stored.userId == owner) return StandIn.Found(stored)
        val own =
            writingLinks {
                val found = deviceStandIn(owner, stored)
                if (active(owner)) found ?: deviceCopy(owner, stored) else null
            }
        return own?.let { StandIn.Found(it) } ?: StandIn.Switched
    }

    private suspend fun standInForFriend(
        viewer: UserId,
        shown: PlacedMachine,
    ): StandIn {
        val group =
            shown.group?.takeIf { it.owner == viewer }
                ?: catching { group(viewer) }.getOrElse { return failedRead(viewer) }
        if (!active(viewer)) return StandIn.Switched
        val own = catching { friendStandIn(viewer, shown.id, group) }
        if (own.isFailure) return failedRead(viewer)
        return own.getOrNull()?.let { StandIn.Found(it) } ?: StandIn.Gone
    }

    private fun failedRead(owner: UserId?) =
        if (active(owner)) StandIn.Offline else StandIn.Switched

    private fun active(owner: UserId?) = accounts.activeId.value == owner

    private suspend fun LinkWriter.deviceCopy(
        owner: UserId?,
        shown: Machine,
    ): Machine {
        val known = knownLinks(owner, shown)
        val now = clock.now()
        val (copy, link) = linkedCopy(shown, owner, now)
        syncAfter = true
        machines.upsert(copy)
        upsert(link)
        val cluster = MachineClusters(known).of(shown.id)
        missingDirectLinks(owner, listOf(copy.id), cluster, known + link, now)
            .forEach { upsert(it) }
        return copy
    }

    /**
     * Runs [write] with every other link write waiting. Each write reads the own links again under
     * the lock, so two writes at once never write the same link. Not re-entrant. Asks for a sync
     * when [write] wrote, unless [requestSync] is false.
     */
    private suspend fun <T> writingLinks(
        requestSync: Boolean = true,
        write: suspend LinkWriter.() -> T,
    ): T =
        linkWrites.withLock {
            val writer = LinkWriter()
            try {
                writer.write()
            } finally {
                if (requestSync && writer.syncAfter) sync.request()
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
        syncAfter = true
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
        return catching {
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
        }.getOrElse { failed ->
            failures.record(failed)
            before + written
        }
    }

    private suspend fun visibleLinks(owner: UserId): List<MachineLink> =
        links.all(owner) + friends.groupLinks(owner)
}

private fun GroupMachines.mateLinks(owner: UserId): List<MachineLink> =
    links.filter { it.userId != owner }
