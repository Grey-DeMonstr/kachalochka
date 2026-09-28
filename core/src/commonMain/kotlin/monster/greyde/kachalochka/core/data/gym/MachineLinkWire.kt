package monster.greyde.kachalochka.core.data.gym

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

const val MACHINE_LINK_TABLE: String = "machine_link"

@Serializable
internal data class MachineLinkRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("machine_id") val machineId: String,
    @SerialName("linked_machine_id") val linkedMachineId: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toMachineLink(): MachineLink =
        MachineLink(
            id = MachineLinkId(id),
            userId = userId?.let(::UserId),
            machineId = MachineId(machineId),
            linkedMachineId = MachineId(linkedMachineId),
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(link: MachineLink): MachineLinkRow =
            MachineLinkRow(
                id = link.id.value,
                userId = link.userId?.value,
                machineId = link.machineId.value,
                linkedMachineId = link.linkedMachineId.value,
                updatedAt = link.updatedAt.toString(),
                deleted = link.deleted,
            )
    }
}
