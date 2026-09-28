package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MachineLinkWireTest {
    @Test
    fun a_link_survives_the_wire_round_trip() {
        val link =
            MachineLink(
                id = MachineLinkId("9b1f0c3e-0000-4000-8000-000000000001"),
                userId = UserId("9b1f0c3e-0000-4000-8000-000000000002"),
                machineId = MachineId("9b1f0c3e-0000-4000-8000-000000000003"),
                linkedMachineId = MachineId("9b1f0c3e-0000-4000-8000-000000000004"),
                updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_123),
                deleted = true,
            )

        assertEquals(link, MachineLinkRow.of(link).toMachineLink())
    }
}
