package monster.greyde.kachalochka.core.domain.friends

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.identity.requireUuidV4
import kotlin.jvm.JvmInline

@JvmInline
value class GroupId(
    val value: String,
) {
    init {
        requireUuidV4(value, "GroupId")
    }
}

data class FriendGroup(
    val id: GroupId,
    val name: String,
    val ownerId: UserId,
    val inviteCode: String,
    val memberCount: Int,
)

data class GroupMember(
    val userId: UserId,
    val displayName: String,
    val isOwner: Boolean,
)

/** Someone sharing a live group with the viewer; they may share several, so no owner mark. */
data class Friend(
    val userId: UserId,
    val displayName: String,
)

data class FriendMachine(
    val machine: Machine,
    val owner: Friend,
)

data class FriendVisit(
    val friend: Friend,
    val visit: Visit,
)

/**
 * A friend's sets on a linked machine from their latest visit on it, in visit order. [machine] is
 * the friend's own, which the first set was recorded on: its unit is the one the weights are in.
 */
data class FriendResult(
    val friend: Friend,
    val machine: Machine,
    val sets: List<WorkoutSet>,
)

/**
 * Friends' rows, read online and never stored; row-level security decides whose rows come back.
 * Every call acts as the active account.
 */
interface FriendsRepository {
    /** The live groups the account belongs to, by name. */
    suspend fun groups(): List<FriendGroup>

    /** Null once the group is deleted or the account is no longer in it. */
    suspend fun group(id: GroupId): FriendGroup?

    suspend fun create(name: String): GroupId

    /** Null when no live group has [code]. */
    suspend fun join(code: String): GroupId?

    suspend fun leave(group: GroupId)

    suspend fun delete(group: GroupId)

    /** The owner first, then by name. */
    suspend fun members(group: FriendGroup): List<GroupMember>

    /** [member]'s live visits, newest first, including those without a day yet. */
    suspend fun visits(member: UserId): List<Visit>

    /** [visit]'s live sets in visit order. */
    suspend fun sets(visit: Visit): List<WorkoutSet>

    suspend fun machines(member: UserId): List<Machine>

    /** The live machines of everyone sharing a group with [viewer], by name. */
    suspend fun groupMachines(viewer: UserId): List<FriendMachine>

    /** Group mates' live visits on days [from]..[to], or recorded within a day of them. */
    suspend fun groupVisits(
        viewer: UserId,
        from: CalendarDay,
        to: CalendarDay,
    ): List<FriendVisit>

    /** The live links of everyone sharing a group with [viewer]. */
    suspend fun groupLinks(viewer: UserId): List<MachineLink>

    /** Deletes friends' links into the account's own [machine]. */
    suspend fun breakLinks(machine: MachineId)

    /** Moves friends' links into the account's own [removed] over to [kept]. */
    suspend fun repointLinks(
        removed: MachineId,
        kept: MachineId,
    )

    /** Up to three friends' latest visits on any of [machines], newest first. */
    suspend fun latestOn(
        viewer: UserId,
        machines: Set<MachineId>,
    ): List<FriendResult>
}
