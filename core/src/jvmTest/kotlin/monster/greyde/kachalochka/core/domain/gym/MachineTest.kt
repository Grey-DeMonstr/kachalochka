package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MachineTest {
    @Test
    fun a_new_machine_carries_the_form_defaults() {
        val now = Instant.fromEpochSeconds(1_700_000_000)

        val machine = Machine.new("Жим ногами", userId = null, now = now)

        assertEquals("Жим ногами", machine.name)
        assertEquals("", machine.setupNote)
        assertEquals(WeightMode.Total, machine.weightMode)
        assertEquals(0.0, machine.platformWeight)
        assertEquals(false, machine.platformIncluded)
        assertEquals(WeightUnit.Kg, machine.unit)
        assertEquals(2.5, machine.weightStep)
        assertEquals(now, machine.updatedAt)
        assertEquals(false, machine.deleted)
    }

    @Test
    fun the_weight_steps_are_the_four_the_form_offers() {
        assertEquals(listOf(1.0, 2.5, 5.0, 10.0), Machine.WEIGHT_STEPS)
    }
}
