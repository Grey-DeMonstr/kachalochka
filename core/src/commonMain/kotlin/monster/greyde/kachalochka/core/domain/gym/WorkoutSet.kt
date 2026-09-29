package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

data class WorkoutSet(
    val id: WorkoutSetId,
    val userId: UserId?,
    val visitId: VisitId,
    val machineId: MachineId,
    val weight: Double,
    val reps: Int,
    val position: Int,
    val recordedAt: Instant,
    val updatedAt: Instant,
    val deleted: Boolean,
    val comment: String = "",
)

/** A visit's sets run by position; sets at the same position keep their recording order. */
val visitOrder: Comparator<WorkoutSet> =
    compareBy<WorkoutSet>({ it.position }, { it.recordedAt }, { it.id.value })

data class SetValues(
    val weight: Double,
    val reps: Int,
)
