package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

enum class WeightMode { Total, PerSide, Counterweight }

enum class WeightUnit { Kg, Lb }

data class Machine(
    val id: MachineId,
    val userId: UserId?,
    val name: String,
    val setupNote: String,
    val weightMode: WeightMode,
    val platformWeight: Double,
    val platformIncluded: Boolean,
    val unit: WeightUnit,
    val weightStep: Double,
    val updatedAt: Instant,
    val deleted: Boolean,
) {
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
                weightStep = 2.5,
                updatedAt = now,
                deleted = false,
            )
    }
}
