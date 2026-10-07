package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class SideConversionTest {
    private val set =
        WorkoutSet(
            WorkoutSetId.random(),
            null,
            VisitId.random(),
            MachineId.random(),
            58.0,
            10,
            0,
            T0,
            T0,
            false,
        )

    @Test
    fun a_total_counted_per_side_halves_and_back_doubles() {
        assertEquals(0.5, sideFactor(WeightMode.Total, WeightMode.PerSide))
        assertEquals(2.0, sideFactor(WeightMode.PerSide, WeightMode.Total))
    }

    @Test
    fun a_counterweight_or_no_change_keeps_the_weight() {
        assertEquals(1.0, sideFactor(WeightMode.Total, WeightMode.Counterweight))
        assertEquals(1.0, sideFactor(WeightMode.Counterweight, WeightMode.PerSide))
        assertEquals(1.0, sideFactor(WeightMode.PerSide, WeightMode.PerSide))
    }

    @Test
    fun sided_sets_scale_their_weights() {
        val now = T0 + 1.minutes

        assertEquals(
            listOf(set.copy(weight = 29.0, updatedAt = now)),
            sidedSets(listOf(set), 0.5, now),
        )
    }
}
