package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MachineLinkTest {
    @Test
    fun a_machine_cannot_be_linked_to_itself() {
        val press = MachineId.random()

        assertFailsWith<IllegalArgumentException> {
            MachineLink(MachineLinkId.random(), null, press, press, T0, false)
        }
    }
}
