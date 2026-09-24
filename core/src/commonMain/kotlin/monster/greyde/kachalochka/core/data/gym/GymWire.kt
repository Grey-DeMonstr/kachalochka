package monster.greyde.kachalochka.core.data.gym

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

const val MACHINE_TABLE: String = "machine"
const val VISIT_TABLE: String = "visit"
const val WORKOUT_SET_TABLE: String = "workout_set"

fun WeightMode.wireName(): String =
    when (this) {
        WeightMode.Total -> "total"
        WeightMode.PerSide -> "per_side"
        WeightMode.Counterweight -> "counterweight"
    }

fun weightModeOf(wire: String): WeightMode = WeightMode.entries.first { it.wireName() == wire }

fun WeightUnit.wireName(): String =
    when (this) {
        WeightUnit.Kg -> "kg"
        WeightUnit.Lb -> "lb"
    }

fun weightUnitOf(wire: String): WeightUnit = WeightUnit.entries.first { it.wireName() == wire }

@Serializable
internal data class MachineRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    val name: String,
    @SerialName("setup_note") val setupNote: String,
    @SerialName("weight_mode") val weightMode: String,
    @SerialName("platform_weight") val platformWeight: Double,
    @SerialName("platform_included") val platformIncluded: Boolean,
    val unit: String,
    @SerialName("weight_step") val weightStep: Double,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toMachine(): Machine =
        Machine(
            id = MachineId(id),
            userId = userId?.let(::UserId),
            name = name,
            setupNote = setupNote,
            weightMode = weightModeOf(weightMode),
            platformWeight = platformWeight,
            platformIncluded = platformIncluded,
            unit = weightUnitOf(unit),
            weightStep = weightStep,
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(machine: Machine): MachineRow =
            MachineRow(
                id = machine.id.value,
                userId = machine.userId?.value,
                name = machine.name,
                setupNote = machine.setupNote,
                weightMode = machine.weightMode.wireName(),
                platformWeight = machine.platformWeight,
                platformIncluded = machine.platformIncluded,
                unit = machine.unit.wireName(),
                weightStep = machine.weightStep,
                updatedAt = machine.updatedAt.toString(),
                deleted = machine.deleted,
            )
    }
}

@Serializable
internal data class VisitRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("ended_at") val endedAt: String?,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toVisit(): Visit =
        Visit(
            id = VisitId(id),
            userId = userId?.let(::UserId),
            recordedAt = Instant.parse(recordedAt),
            endedAt = endedAt?.let(Instant::parse),
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(visit: Visit): VisitRow =
            VisitRow(
                id = visit.id.value,
                userId = visit.userId?.value,
                recordedAt = visit.recordedAt.toString(),
                endedAt = visit.endedAt?.toString(),
                updatedAt = visit.updatedAt.toString(),
                deleted = visit.deleted,
            )
    }
}

@Serializable
internal data class WorkoutSetRow(
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
