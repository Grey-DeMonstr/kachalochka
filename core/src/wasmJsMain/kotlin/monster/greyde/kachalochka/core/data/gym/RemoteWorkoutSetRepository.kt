package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.identity.UserId

class RemoteWorkoutSetRepository(
    private val client: SupabaseClient,
) : WorkoutSetRepository {
    override suspend fun upsert(set: WorkoutSet) {
        client.postgrest.from(WORKOUT_SET_TABLE).upsert(WorkoutSetRow.of(set))
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
        readPage(match, null).map { it.toWorkoutSet() }

    /** Reads past the row cap, a page of [PAGE_SIZE] at a time, the project's `max_rows`. */
    private suspend fun paged(match: PostgrestFilterBuilder.() -> Unit): List<WorkoutSet> {
        val rows = mutableListOf<WorkoutSetRow>()
        do {
            val page = readPage(match, rows.size.toLong())
            rows += page
        } while (page.size.toLong() == PAGE_SIZE)
        return rows.map { it.toWorkoutSet() }
    }

    private suspend fun readPage(
        match: PostgrestFilterBuilder.() -> Unit,
        from: Long?,
    ): List<WorkoutSetRow> =
        client.postgrest
            .from(WORKOUT_SET_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    match()
                }
                order("recorded_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
                from?.let { range(it, it + PAGE_SIZE - 1) }
            }.decodeList<WorkoutSetRow>()
}

private const val PAGE_SIZE = 1000L
