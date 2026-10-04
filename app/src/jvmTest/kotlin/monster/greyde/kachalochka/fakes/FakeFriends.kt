package monster.greyde.kachalochka.fakes

import kotlinx.coroutines.CompletableDeferred
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendResult
import monster.greyde.kachalochka.core.domain.friends.FriendVisit
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.friends.GroupMember
import monster.greyde.kachalochka.core.domain.friends.friendResults
import monster.greyde.kachalochka.core.domain.friends.latestVisitsByMember
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.machinePeaks
import monster.greyde.kachalochka.core.domain.gym.photoOrder
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** Friends as row-level security shows them to the active account; reads fail while [offline]. */
class FakeFriends(
    private val active: () -> Account?,
) : FriendsRepository {
    val groups = linkedMapOf<GroupId, FriendGroup>()
    val members = linkedMapOf<GroupId, MutableList<Friend>>()
    val visits = mutableListOf<Visit>()
    val sets = mutableListOf<WorkoutSet>()
    val machines = mutableListOf<Machine>()
    val links = mutableListOf<MachineLink>()
    val photos = mutableListOf<Photo>()
    var offline = false
    var reads = 0
        private set

    /** The days every [groupVisits] call asked for, in order. */
    val visitWindows = mutableListOf<Pair<CalendarDay, CalendarDay>>()

    /** The machines every [latestOn] call asked about, in order. */
    val latestOnAsked = mutableListOf<Set<MachineId>>()

    /** While set, a read started now waits for it, keeping it in flight while a test needs. */
    var gate: CompletableDeferred<Unit>? = null

    /** A group [owner] made, with [others] in it. */
    fun group(
        name: String,
        owner: Friend,
        vararg others: Friend,
        code: String = "ABCD2345",
    ): FriendGroup {
        val id = GroupId(Uuid.random().toString())
        groups[id] = FriendGroup(id, name, owner.userId, code, 0)
        members[id] = (listOf(owner) + others).toMutableList()
        return counted(groups.getValue(id))
    }

    private fun me(): Friend? = active()?.let { Friend(it.userId, it.displayName) }

    private suspend fun <T> online(read: () -> T): T {
        reads++
        gate?.await()
        if (offline) error("no connection")
        return read()
    }

    private fun counted(group: FriendGroup) =
        group.copy(memberCount = members[group.id].orEmpty().size)

    private fun visible(id: GroupId) = members[id].orEmpty().any { it.userId == me()?.userId }

    private fun matesById(viewer: UserId): Map<UserId, Friend> =
        members.values
            .filter { list -> list.any { it.userId == viewer } }
            .flatten()
            .filter { it.userId != viewer }
            .associateBy { it.userId }

    /** [member]'s own rows, or a group-mate's: what `shares_group_with` widens the server to. */
    private fun visibleMember(member: UserId): Boolean {
        val myId = me()?.userId ?: return false
        return member == myId || member in matesById(myId).keys
    }

    override suspend fun groups() =
        online {
            groups.values
                .filter { visible(it.id) }
                .map(::counted)
                .sortedBy { it.name.lowercase() }
        }

    override suspend fun group(id: GroupId) =
        online { groups[id]?.takeIf { visible(id) }?.let(::counted) }

    override suspend fun create(name: String) =
        online { group(name.trim(), checkNotNull(me()), code = "NEWG2345").id }

    override suspend fun join(code: String) =
        online {
            val id = groups.values.firstOrNull { it.inviteCode == code }?.id ?: return@online null
            val mine = checkNotNull(me())
            members.getValue(id).apply { if (none { it.userId == mine.userId }) add(mine) }
            id
        }

    override suspend fun leave(group: GroupId) =
        online {
            members[group]?.removeAll { it.userId == me()?.userId }
            Unit
        }

    override suspend fun delete(group: GroupId) =
        online {
            groups.remove(group)
            members.remove(group)
            Unit
        }

    override suspend fun members(group: FriendGroup) =
        online {
            members[group.id]
                .orEmpty()
                .map {
                    GroupMember(
                        it.userId,
                        it.displayName,
                        it.userId == group.ownerId,
                        it.avatar,
                    )
                }.sortedWith(
                    compareByDescending<GroupMember> { it.isOwner }
                        .thenBy { it.displayName.lowercase() },
                )
        }

    override suspend fun visits(member: UserId) =
        online {
            if (!visibleMember(member)) return@online emptyList()
            visits
                .filter { it.userId == member && !it.deleted }
                .sortedByDescending { it.recordedAt }
        }

    override suspend fun sets(visit: Visit) =
        online {
            val owner = visit.userId?.takeIf { visibleMember(it) } ?: return@online emptyList()
            sets
                .filter { it.visitId == visit.id && it.userId == owner && !it.deleted }
                .sortedWith(visitOrder)
        }

    override suspend fun setsOn(
        member: UserId,
        machine: MachineId,
    ) = online {
        if (!visibleMember(member)) return@online emptyList()
        sets
            .filter { it.userId == member && it.machineId == machine && !it.deleted }
            .sortedWith(compareBy<WorkoutSet> { it.recordedAt }.thenBy { it.id.value })
    }

    override suspend fun machines(member: UserId) =
        online {
            if (!visibleMember(member)) return@online emptyList()
            machines.filter { it.userId == member && !it.deleted }.sortedBy { it.name.lowercase() }
        }

    override suspend fun photos(machine: MachineId) =
        online {
            photos
                .filter { !it.deleted && it.machineId == machine }
                .filter { photo -> photo.userId?.let(::visibleMember) == true }
                .sortedWith(photoOrder)
        }

    override suspend fun mates(viewer: UserId) = online { matesById(viewer).values.toList() }

    override suspend fun groupPhotos(viewer: UserId) =
        online {
            val mates = matesById(viewer)
            photos.filter { !it.deleted && it.userId in mates.keys }
        }

    override suspend fun groupMachines(viewer: UserId) =
        online {
            val mates = matesById(viewer)
            machines
                .filterNot { it.deleted }
                .mapNotNull { m -> m.userId?.let(mates::get)?.let { FriendMachine(m, it) } }
                .sortedBy { it.machine.name.lowercase() }
        }

    override suspend fun groupVisits(
        viewer: UserId,
        from: CalendarDay,
        to: CalendarDay,
    ): List<FriendVisit> {
        visitWindows += from to to
        return online {
            val mates = matesById(viewer).filterKeys(::visibleMember)
            val earliest = from.plusDays(-1).at(0L, Duration.ZERO)
            val latest = to.plusDays(2).at(0L, Duration.ZERO)
            visits
                .filter { !it.deleted }
                .filter { visit ->
                    val day = visit.day
                    if (day != null) day in from..to else visit.recordedAt in earliest..<latest
                }.mapNotNull { v -> v.userId?.let(mates::get)?.let { FriendVisit(it, v) } }
        }
    }

    override suspend fun groupLinks(viewer: UserId) =
        online {
            val mates = matesById(viewer)
            links.filter { !it.deleted && it.userId in mates.keys }
        }

    override suspend fun groupPeaks(viewer: UserId) =
        online {
            val mates = matesById(viewer)
            machinePeaks(sets.filter { it.userId in mates.keys })
        }

    /** The machines every [breakLinks] call named, in order. */
    val broken = mutableListOf<MachineId>()

    /** The (removed, kept) pairs every [repointLinks] call named, in order. */
    val repointed = mutableListOf<Pair<MachineId, MachineId>>()

    override suspend fun breakLinks(machine: MachineId) =
        online {
            broken += machine
            links.replaceAll { if (it.linkedMachineId == machine) it.copy(deleted = true) else it }
        }

    override suspend fun repointLinks(
        removed: MachineId,
        kept: MachineId,
    ) = online {
        repointed += removed to kept
        val me = me()?.userId
        links.replaceAll {
            if (it.linkedMachineId == removed && it.userId != me) {
                it.copy(linkedMachineId = kept)
            } else {
                it
            }
        }
    }

    override suspend fun latestOn(
        viewer: UserId,
        machines: Set<MachineId>,
    ): List<FriendResult> {
        latestOnAsked += machines
        return online {
            val mates = matesById(viewer)
            val ids =
                this.machines
                    .filter { !it.deleted && it.id in machines && it.userId in mates.keys }
                    .map { it.id }
                    .toSet()
            val onThem = sets.filter { !it.deleted && it.machineId in ids }
            friendResults(
                mates.values.toList(),
                latestVisitsByMember(onThem, 3),
                onThem,
                this.machines,
            )
        }
    }
}
