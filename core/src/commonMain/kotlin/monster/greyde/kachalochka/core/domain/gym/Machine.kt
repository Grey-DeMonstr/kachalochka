package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

enum class WeightMode { Total, PerSide }

enum class WeightUnit { Kg, Lb, Custom }

data class Machine(
    val id: MachineId,
    val userId: UserId?,
    val name: String,
    val setupNote: String,
    val weightMode: WeightMode,
    val platformWeight: Double,
    val platformIncluded: Boolean,
    val unit: WeightUnit,
    val unitLabel: String,
    val weightStep: Double,
    val updatedAt: Instant,
    val deleted: Boolean,
    val linkId: MachineId?,
) {
    /** Machines sharing this key are one physical machine; an unlinked one is its own key. */
    val linkKey: MachineId
        get() = linkId ?: id

    companion object {
        val WEIGHT_STEPS: List<Double> = listOf(1.0, 2.5, 5.0, 10.0)

        fun new(
            name: String,
            userId: UserId?,
            now: Instant,
        ): Machine =
            Machine(
                id = MachineId.random(),
                userId = userId,
                name = name,
                setupNote = "",
                weightMode = WeightMode.Total,
                platformWeight = 0.0,
                platformIncluded = false,
                unit = WeightUnit.Kg,
                unitLabel = "",
                weightStep = 2.5,
                updatedAt = now,
                deleted = false,
                linkId = null,
            )
    }
}

/** [owner]'s own row for a friend's machine: the friend's settings, one physical machine. */
fun linkedCopy(
    friend: Machine,
    owner: UserId?,
    now: Instant,
): Machine =
    friend.copy(
        id = MachineId.random(),
        userId = owner,
        linkId = friend.linkKey,
        updatedAt = now,
        deleted = false,
    )
