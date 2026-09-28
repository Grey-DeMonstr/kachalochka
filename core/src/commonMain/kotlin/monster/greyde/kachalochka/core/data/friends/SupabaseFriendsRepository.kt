package monster.greyde.kachalochka.core.data.friends

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.MachineLinkRow
import monster.greyde.kachalochka.core.data.gym.MachineRow
import monster.greyde.kachalochka.core.data.gym.PHOTO_TABLE
import monster.greyde.kachalochka.core.data.gym.PhotoRow
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.VisitRow
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.gym.WorkoutSetRow
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
import monster.greyde.kachalochka.core.domain.gym.photoOrder
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Duration

// join_group raises it for a code no live group has, and PostgREST answers it with a 404; see
// 0007_groups.sql.
private const val UNKNOWN_INVITE_CODE = "PT404"
private const val NOT_FOUND = 404

private const val FRIEND_RESULTS = 3

/**
 * One implementation for both platforms (tech spec §3): friends are read online and nothing here
 * is written to SQLite. Row-level security decides whose rows come back, so every read names the
 * member it wants rather than trusting the viewer's filter.
 */
class SupabaseFriendsRepository(
    private val client: Lazy<SupabaseClient>,
    private val clock: Clock,
) : FriendsRepository {
    private val postgrest: Postgrest get() = client.value.postgrest

    override suspend fun groups(): List<FriendGroup> =
        coroutineScope {
            val rows =
                async {
                    postgrest
                        .from(FRIEND_GROUP_TABLE)
                        .select { filter { eq("deleted", false) } }
                        .decodeList<FriendGroupRow>()
                }
            val members = async { memberships() }
            val counts = members.await().groupingBy { it.groupId }.eachCount()
            rows.await().map { it.toGroup(counts[it.id] ?: 0) }.sortedBy { it.name.lowercase() }
        }

    override suspend fun group(id: GroupId): FriendGroup? {
        val row =
            postgrest
                .from(FRIEND_GROUP_TABLE)
                .select {
                    filter {
                        eq("id", id.value)
                        eq("deleted", false)
                    }
                }.decodeSingleOrNull<FriendGroupRow>() ?: return null
        return row.toGroup(memberships(id).size)
    }

    override suspend fun create(name: String): GroupId =
        GroupId(
            postgrest
                .rpc("create_group", buildJsonObject { put("group_name", name.trim()) })
                .decodeAs<String>(),
        )

    // Block-bodied with an early return: wasm-opt crashes on this try as an expression body.
    override suspend fun join(code: String): GroupId? {
        val joined =
            try {
                postgrest.rpc("join_group", buildJsonObject { put("code", code) })
            } catch (refused: PostgrestRestException) {
                if (refused.code == UNKNOWN_INVITE_CODE || refused.statusCode == NOT_FOUND) {
                    return null
                }
                throw refused
            }
        return GroupId(joined.decodeAs<String>())
    }

    override suspend fun leave(group: GroupId) {
        postgrest.rpc("leave_group", buildJsonObject { put("target", group.value) })
    }

    override suspend fun delete(group: GroupId) {
        postgrest
            .from(FRIEND_GROUP_TABLE)
            .update(GroupDeletion(deleted = true, updatedAt = clock.now().toString())) {
                filter { eq("id", group.value) }
            }
    }

    override suspend fun members(group: FriendGroup): List<GroupMember> =
        memberships(group.id)
            .map {
                GroupMember(UserId(it.userId), it.displayName, it.userId == group.ownerId.value)
            }.sortedWith(
                compareByDescending<GroupMember> { it.isOwner }
                    .thenBy { it.displayName.lowercase() },
            )

    override suspend fun visits(member: UserId): List<Visit> =
        postgrest
            .from(VISIT_TABLE)
            .select {
                filter {
                    eq("user_id", member.value)
                    eq("deleted", false)
                }
                order("recorded_at", Order.DESCENDING)
            }.decodeList<VisitRow>()
            .map { it.toVisit() }

    override suspend fun sets(visit: Visit): List<WorkoutSet> {
        val owner = visit.userId ?: return emptyList()
        return liveSets {
            eq("visit_id", visit.id.value)
            eq("user_id", owner.value)
        }.sortedWith(visitOrder)
    }

    override suspend fun machines(member: UserId): List<Machine> =
        liveMachines { eq("user_id", member.value) }.sortedBy { it.name.lowercase() }

    override suspend fun photos(machine: MachineId): List<Photo> =
        postgrest
            .from(PHOTO_TABLE)
            .select {
                filter {
                    eq("machine_id", machine.value)
                    eq("deleted", false)
                }
            }.decodeList<PhotoRow>()
            .map { it.toPhoto() }
            .sortedWith(photoOrder)

    override suspend fun groupPhotos(viewer: UserId): List<Photo> {
        val mates = mates(viewer)
        if (mates.isEmpty()) return emptyList()
        return postgrest
            .from(PHOTO_TABLE)
            .select {
                filter {
                    isIn("user_id", mates.keys.map { it.value })
                    eq("deleted", false)
                }
            }.decodeList<PhotoRow>()
            .map { it.toPhoto() }
    }

    override suspend fun groupMachines(viewer: UserId): List<FriendMachine> {
        val mates = mates(viewer)
        if (mates.isEmpty()) return emptyList()
        return liveMachines { isIn("user_id", mates.keys.map { it.value }) }
            .mapNotNull { machine ->
                machine.userId?.let(mates::get)?.let { FriendMachine(machine, it) }
            }.sortedBy { it.machine.name.lowercase() }
    }

    override suspend fun groupVisits(
        viewer: UserId,
        from: CalendarDay,
        to: CalendarDay,
    ): List<FriendVisit> {
        val mates = mates(viewer)
        if (mates.isEmpty()) return emptyList()
        // Whatever the viewer's offset, a day's instants fall within a day either side of it.
        val earliest = from.plusDays(-1).at(0L, Duration.ZERO)
        val latest = to.plusDays(2).at(0L, Duration.ZERO)
        return postgrest
            .from(VISIT_TABLE)
            .select {
                filter {
                    isIn("user_id", mates.keys.map { it.value })
                    eq("deleted", false)
                    or {
                        and {
                            gte("day", from.iso)
                            lte("day", to.iso)
                        }
                        and {
                            exact("day", null)
                            gte("recorded_at", earliest.toString())
                            lt("recorded_at", latest.toString())
                        }
                    }
                }
            }.decodeList<VisitRow>()
            .map { it.toVisit() }
            .mapNotNull { visit -> visit.userId?.let(mates::get)?.let { FriendVisit(it, visit) } }
    }

    override suspend fun groupLinks(viewer: UserId): List<MachineLink> {
        val mates = mates(viewer)
        if (mates.isEmpty()) return emptyList()
        return postgrest
            .from(MACHINE_LINK_TABLE)
            .select {
                filter {
                    isIn("user_id", mates.keys.map { it.value })
                    eq("deleted", false)
                }
            }.decodeList<MachineLinkRow>()
            .map { it.toMachineLink() }
    }

    // Friends' rows can be written only by the definer functions of 0009_machine_links.sql.
    override suspend fun breakLinks(machine: MachineId) {
        postgrest.rpc("break_machine_links", buildJsonObject { put("target", machine.value) })
    }

    override suspend fun repointLinks(
        removed: MachineId,
        kept: MachineId,
    ) {
        postgrest.rpc(
            "repoint_machine_links",
            buildJsonObject {
                put("removed", removed.value)
                put("kept", kept.value)
            },
        )
    }

    override suspend fun latestOn(
        viewer: UserId,
        machines: Set<MachineId>,
    ): List<FriendResult> {
        if (machines.isEmpty()) return emptyList()
        val mates = mates(viewer)
        if (mates.isEmpty()) return emptyList()
        val mateIds = mates.keys.map { it.value }
        val candidates =
            liveMachines {
                isIn("id", machines.map { it.value })
                isIn("user_id", mateIds)
            }
        if (candidates.isEmpty()) return emptyList()
        val machineIds = candidates.map { it.id.value }
        // One request per friend, so a friend's long history never crowds another's out.
        val newest =
            coroutineScope {
                candidates
                    .mapNotNull { it.userId }
                    .distinct()
                    .map { owner ->
                        async {
                            liveSets(newestFirst = 1) {
                                isIn("machine_id", machineIds)
                                eq("user_id", owner.value)
                            }
                        }
                    }.awaitAll()
                    .flatten()
            }
        val visits = latestVisitsByMember(newest, FRIEND_RESULTS)
        if (visits.isEmpty()) return emptyList()
        val visitSets =
            liveSets {
                isIn("visit_id", visits.map { it.value })
                isIn("machine_id", machineIds)
                isIn("user_id", mateIds)
            }
        return friendResults(mates.values.toList(), visits, visitSets, candidates)
    }

    private suspend fun memberships(group: GroupId? = null): List<GroupMemberRow> =
        postgrest
            .from(GROUP_MEMBER_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    if (group != null) eq("group_id", group.value)
                }
            }.decodeList<GroupMemberRow>()

    private suspend fun mates(viewer: UserId): Map<UserId, Friend> =
        memberships()
            .filter { it.userId != viewer.value }
            .associate { UserId(it.userId) to Friend(UserId(it.userId), it.displayName) }

    private suspend fun liveMachines(match: PostgrestFilterBuilder.() -> Unit): List<Machine> =
        postgrest
            .from(MACHINE_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    match()
                }
            }.decodeList<MachineRow>()
            .map { it.toMachine() }

    private suspend fun liveSets(
        newestFirst: Long? = null,
        match: PostgrestFilterBuilder.() -> Unit,
    ): List<WorkoutSet> =
        postgrest
            .from(WORKOUT_SET_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    match()
                }
                if (newestFirst != null) {
                    order("recorded_at", Order.DESCENDING)
                    limit(newestFirst)
                }
            }.decodeList<WorkoutSetRow>()
            .map { it.toWorkoutSet() }
}
