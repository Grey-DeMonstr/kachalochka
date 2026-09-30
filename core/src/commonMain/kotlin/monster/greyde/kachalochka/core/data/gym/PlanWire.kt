package monster.greyde.kachalochka.core.data.gym

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

const val PLAN_TABLE: String = "workout_plan"

@Serializable
internal data class PlanRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    val name: String,
    @SerialName("machine_ids") val machineIds: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toPlan(): Plan =
        Plan(
            id = PlanId(id),
            userId = userId?.let(::UserId),
            name = name,
            machineIds = machineIdsOf(machineIds),
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(plan: Plan): PlanRow =
            PlanRow(
                id = plan.id.value,
                userId = plan.userId?.value,
                name = plan.name,
                machineIds = machineIdsText(plan.machineIds),
                createdAt = plan.createdAt.toString(),
                updatedAt = plan.updatedAt.toString(),
                deleted = plan.deleted,
            )
    }
}
