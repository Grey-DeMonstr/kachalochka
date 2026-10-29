package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import monster.greyde.kachalochka.core.data.supabase.pagedRows
import monster.greyde.kachalochka.core.domain.gym.DayBest
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

class RemoteWorkoutSetRepository(
    private val client: SupabaseClient,
) : WorkoutSetRepository {
    override suspend fun upsert(set: WorkoutSet) {
        client.postgrest.from(WORKOUT_SET_TABLE).upsert(WorkoutSetRow.of(set))
    }

    override suspend fun storePriorBest(
        id: WorkoutSetId,
        best: DayBest,
        updatedAt: Instant,
    ) {
        client.postgrest
            .from(WORKOUT_SET_TABLE)
            .update(PriorBestPatch(best.weight, best.reps, best.sets, updatedAt.toString())) {
                filter { eq("id", id.value) }
            }
    }

    override suspend fun forVisit(visitId: VisitId): List<WorkoutSet> =
        live { eq("visit_id", visitId.value) }.sortedWith(visitOrder)

    override suspend fun forMachine(machineId: MachineId): List<WorkoutSet> =
        paged { eq("machine_id", machineId.value) }

    override suspend fun all(owner: UserId?): List<WorkoutSet> = paged { owned(owner) }

    // PostgREST has no per-group maximum, so the newest set of each machine is picked here.
    override suspend fun latestPerMachine(owner: UserId?): List<WorkoutSet> =
        paged { owned(owner) }.sortedByDescending { it.recordedAt }.distinctBy { it.machineId }

    override suspend fun peaks(owner: UserId?): List<MachinePeaks> =
        client.postgrest.machinePeaks(listOfNotNull(owner))

    private suspend fun live(match: PostgrestFilterBuilder.() -> Unit): List<WorkoutSet> =
        client.postgrest
            .from(WORKOUT_SET_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    match()
                }
            }.decodeList<WorkoutSetRow>()
            .map { it.toWorkoutSet() }

    private suspend fun paged(match: PostgrestFilterBuilder.() -> Unit): List<WorkoutSet> =
        client.postgrest
            .pagedRows<WorkoutSetRow>(WORKOUT_SET_TABLE, listOf("recorded_at", "id")) {
                eq("deleted", false)
                match()
            }.map { it.toWorkoutSet() }
}
