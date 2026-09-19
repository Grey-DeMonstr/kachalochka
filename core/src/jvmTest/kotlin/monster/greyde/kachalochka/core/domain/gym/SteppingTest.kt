package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals

class SteppingTest {
    @Test
    fun weight_moves_by_the_step() {
        assertEquals(72.5, stepWeight(70.0, 2.5, +1))
        assertEquals(67.5, stepWeight(70.0, 2.5, -1))
    }

    @Test
    fun weight_never_goes_below_zero() {
        assertEquals(0.0, stepWeight(1.0, 2.5, -1))
    }

    @Test
    fun repeated_steps_do_not_drift() {
        var weight = 0.0
        repeat(10) { weight = stepWeight(weight, 0.1, +1) }

        assertEquals(1.0, weight)
    }

    @Test
    fun reps_move_by_one_and_stay_at_least_one() {
        assertEquals(11, stepReps(10, +1))
        assertEquals(1, stepReps(1, -1))
    }
}
