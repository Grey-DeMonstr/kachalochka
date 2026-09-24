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

/** The web repositories' PostgREST calls, reused here so both clients share one wire shape. */
class SupabaseSyncGateway(
    private val client: Lazy<SupabaseClient>,
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
    ): List<Machine> = pullAll<MachineRow>(MACHINE_TABLE, owner, since).map { it.toMachine() }

    override suspend fun pullVisits(
        owner: UserId,
        since: Instant?,
    ): List<Visit> = pullAll<VisitRow>(VISIT_TABLE, owner, since).map { it.toVisit() }

    override suspend fun pullSets(
        owner: UserId,
        since: Instant?,
    ): List<WorkoutSet> =
        pullAll<WorkoutSetRow>(WORKOUT_SET_TABLE, owner, since).map { it.toWorkoutSet() }

    override suspend fun pullProfiles(
        owner: UserId,
        since: Instant?,
    ): List<Profile> = pullAll<ProfileRow>(PROFILE_TABLE, owner, since).map { it.toProfile() }

    // A page is capped by the project's max_rows (1000 by default), so a first pull on a new
    // device can exceed one page and has to be walked to the end.
    private suspend inline fun <reified R : Any> pullAll(
        table: String,
        owner: UserId,
        since: Instant?,
    ): List<R> {
        val rows = mutableListOf<R>()
        do {
            val page =
                client.value.postgrest
                    .from(table)
                    .select {
                        filter {
                            owned(owner)
                            if (since != null) gt("updated_at", since.toString())
                        }
                        order("updated_at", Order.ASCENDING)
                        order("id", Order.ASCENDING)
                        range(rows.size.toLong(), rows.size + PAGE_SIZE - 1L)
                    }.decodeList<R>()
            rows += page
        } while (page.isNotEmpty())
        return rows
    }
}
