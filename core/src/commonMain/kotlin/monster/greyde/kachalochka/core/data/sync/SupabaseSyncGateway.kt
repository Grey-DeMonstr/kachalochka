package monster.greyde.kachalochka.core.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.MachineRow
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.VisitRow
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.gym.WorkoutSetRow
import monster.greyde.kachalochka.core.data.gym.owned
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.data.profile.ProfileRow
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import kotlin.time.Instant

private const val PAGE_SIZE = 1000L

/**
 * Talks to PostgREST through the same wire rows the web repositories use for these tables; the
 * calls themselves are not shared.
 */
class SupabaseSyncGateway(
    private val client: Lazy<SupabaseClient>,
    internal val pageSize: Long = PAGE_SIZE,
) : SyncGateway {
    override suspend fun pushMachine(machine: Machine) {
        client.value.postgrest
            .from(MACHINE_TABLE)
            .upsert(MachineRow.of(machine))
    }

    override suspend fun pushVisit(visit: Visit) {
        client.value.postgrest
            .from(VISIT_TABLE)
            .upsert(VisitRow.of(visit))
    }

    override suspend fun pushSet(set: WorkoutSet) {
        client.value.postgrest
            .from(WORKOUT_SET_TABLE)
            .upsert(WorkoutSetRow.of(set))
    }

    override suspend fun pushProfile(profile: Profile) {
        client.value.postgrest
            .from(PROFILE_TABLE)
            .upsert(ProfileRow.of(profile))
    }

    override suspend fun pullMachines(
        owner: UserId,
        since: Instant?,
    ): List<Machine> =
        pullAll<MachineRow>(MACHINE_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toMachine() }

    override suspend fun pullVisits(
        owner: UserId,
        since: Instant?,
    ): List<Visit> =
        pullAll<VisitRow>(VISIT_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toVisit() }

    override suspend fun pullSets(
        owner: UserId,
        since: Instant?,
    ): List<WorkoutSet> =
        pullAll<WorkoutSetRow>(WORKOUT_SET_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toWorkoutSet() }

    override suspend fun pullProfiles(
        owner: UserId,
        since: Instant?,
    ): List<Profile> =
        pullAll<ProfileRow>(PROFILE_TABLE, owner, since) { it.updatedAt to it.id }
            .map { it.toProfile() }

    // Keyset paging, not offset: a row another device edits between two page fetches would shift
    // every later row's offset by one, so offset paging can skip a row while the watermark still
    // advances past it. The cursor is the previous page's last row's own (updated_at, id), taken
    // from the server's response so no reformatting can lose precision the server wouldn't accept
    // back.
    private suspend inline fun <reified R : Any> pullAll(
        table: String,
        owner: UserId,
        since: Instant?,
        key: (R) -> Pair<String, String>,
    ): List<R> {
        val rows = mutableListOf<R>()
        var after: Pair<String, String>? = null
        do {
            val page =
                client.value.postgrest
                    .from(table)
                    .select {
                        filter {
                            owned(owner)
                            if (since != null) gt("updated_at", since.toString())
                            after?.let { (updatedAt, id) ->
                                or {
                                    gt("updated_at", updatedAt)
                                    and {
                                        eq("updated_at", updatedAt)
                                        gt("id", id)
                                    }
                                }
                            }
                        }
                        order("updated_at", Order.ASCENDING)
                        order("id", Order.ASCENDING)
                        limit(pageSize)
                    }.decodeList<R>()
            rows += page
            after = page.lastOrNull()?.let(key)
        } while (page.isNotEmpty())
        return rows
    }
}
