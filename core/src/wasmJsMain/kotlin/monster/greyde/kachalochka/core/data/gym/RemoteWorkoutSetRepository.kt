package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

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

private fun PostgrestFilterBuilder.owned(owner: UserId?) {
    if (owner != null) {
        eq("user_id", owner.value)
    } else {
        exact("user_id", null)
    }
}

@Serializable
private data class WorkoutSetRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("visit_id") val visitId: String,
    @SerialName("machine_id") val machineId: String,
    val weight: Double,
    val reps: Int,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toWorkoutSet(): WorkoutSet =
        WorkoutSet(
            id = WorkoutSetId(id),
            userId = userId?.let(::UserId),
            visitId = VisitId(visitId),
            machineId = MachineId(machineId),
            weight = weight,
            reps = reps,
            recordedAt = Instant.parse(recordedAt),
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(set: WorkoutSet): WorkoutSetRow =
            WorkoutSetRow(
                id = set.id.value,
                userId = set.userId?.value,
                visitId = set.visitId.value,
                machineId = set.machineId.value,
                weight = set.weight,
                reps = set.reps,
                recordedAt = set.recordedAt.toString(),
                updatedAt = set.updatedAt.toString(),
                deleted = set.deleted,
            )
    }
}
