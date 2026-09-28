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
) {
    companion object {
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
            )
    }
}

/** [owner]'s copy of a friend's machine, and the link that makes them one machine. */
fun linkedCopy(
    friend: Machine,
    owner: UserId?,
    now: Instant,
): Pair<Machine, MachineLink> {
    val copy =
        friend.copy(
            id = MachineId.random(),
            userId = owner,
            updatedAt = now,
            deleted = false,
        )
    return copy to MachineLink(MachineLinkId.random(), owner, copy.id, friend.id, now, false)
}
