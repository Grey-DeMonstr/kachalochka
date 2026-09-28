package monster.greyde.kachalochka.fakes

import kotlinx.coroutines.CompletableDeferred
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.friends.GroupMember
import monster.greyde.kachalochka.core.domain.friends.friendResults
import monster.greyde.kachalochka.core.domain.friends.latestVisitsByMember
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.identity.UserId
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
    var offline = false
    var reads = 0
        private set

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

    private fun mates(viewer: UserId): Map<UserId, Friend> =
        members.values
            .filter { list -> list.any { it.userId == viewer } }
            .flatten()
            .filter { it.userId != viewer }
            .associateBy { it.userId }

    /** [member]'s own rows, or a group-mate's: what `shares_group_with` widens the server to. */
    private fun visibleMember(member: UserId): Boolean {
        val myId = me()?.userId ?: return false
        return member == myId || member in mates(myId).keys
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
                .map { GroupMember(it.userId, it.displayName, it.userId == group.ownerId) }
                .sortedWith(
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

    override suspend fun machines(member: UserId) =
        online {
            if (!visibleMember(member)) return@online emptyList()
            machines.filter { it.userId == member && !it.deleted }.sortedBy { it.name.lowercase() }
        }

    override suspend fun groupMachines(viewer: UserId) =
        online {
            val mates = mates(viewer)
            machines
                .filterNot { it.deleted }
                .mapNotNull { m -> m.userId?.let(mates::get)?.let { FriendMachine(m, it) } }
                .sortedBy { it.machine.name.lowercase() }
        }

    override suspend fun latestOn(
        viewer: UserId,
        linkKey: MachineId,
    ) = online {
        val mates = mates(viewer)
        val ids =
            machines
                .filter { !it.deleted && it.linkKey == linkKey && it.userId in mates.keys }
                .map { it.id }
                .toSet()
        val onThem = sets.filter { !it.deleted && it.machineId in ids }
        friendResults(mates.values.toList(), latestVisitsByMember(onThem, 3), onThem)
    }
}
