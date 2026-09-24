package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.UserId

class RemoteWorkoutSetRepository(
    private val client: SupabaseClient,
) : WorkoutSetRepository {
    override suspend fun upsert(set: WorkoutSet) {
        client.postgrest.from(WORKOUT_SET_TABLE).upsert(WorkoutSetRow.of(set))
    }

    override suspend fun forVisit(visitId: VisitId): List<WorkoutSet> =
        live { eq("visit_id", visitId.value) }

    override suspend fun forMachine(machineId: MachineId): List<WorkoutSet> =
        live { eq("machine_id", machineId.value) }

    // PostgREST has no per-group maximum, so the newest set of each machine is picked here.
    override suspend fun latestPerMachine(owner: UserId?): List<WorkoutSet> =
        live { owned(owner) }.sortedByDescending { it.recordedAt }.distinctBy { it.machineId }

    private suspend fun live(match: PostgrestFilterBuilder.() -> Unit): List<WorkoutSet> =
        client.postgrest
            .from(WORKOUT_SET_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    match()
                }
                order("recorded_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
            }.decodeList<WorkoutSetRow>()
            .map { it.toWorkoutSet() }
}
