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
    val recordedAt: Instant,
    val updatedAt: Instant,
    val deleted: Boolean,
)

data class SetValues(
    val weight: Double,
    val reps: Int,
)
