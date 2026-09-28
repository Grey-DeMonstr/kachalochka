package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.identity.newUuidV4
import monster.greyde.kachalochka.core.domain.identity.requireUuidV4
import kotlin.jvm.JvmInline
import kotlin.time.Instant

@JvmInline
value class MachineLinkId(
    val value: String,
) {
    init {
        requireUuidV4(value, "MachineLinkId")
    }

    companion object {
        fun random(): MachineLinkId = MachineLinkId(newUuidV4())
    }
}

/** [userId]'s machine [machineId] is the same physical machine as a friend's [linkedMachineId]. */
data class MachineLink(
    val id: MachineLinkId,
    val userId: UserId?,
    val machineId: MachineId,
    val linkedMachineId: MachineId,
    val updatedAt: Instant,
    val deleted: Boolean,
)

interface MachineLinkRepository {
    suspend fun upsert(link: MachineLink)

    /** The owner's live links. */
    suspend fun all(owner: UserId?): List<MachineLink>
}
