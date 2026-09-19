package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class GymIdsTest {
    @Test
    fun random_ids_are_valid_and_distinct() {
        assertNotEquals(MachineId.random(), MachineId.random())
        assertNotEquals(VisitId.random(), VisitId.random())
        assertNotEquals(WorkoutSetId.random(), WorkoutSetId.random())
    }

    @Test
    fun an_id_that_is_not_a_uuid_v4_is_rejected() {
        assertFailsWith<IllegalArgumentException> { MachineId("leg-press") }
        assertFailsWith<IllegalArgumentException> {
            VisitId(
                "9B1F0C3E-0000-4000-8000-000000000001",
            )
        }
        assertFailsWith<IllegalArgumentException> { WorkoutSetId("") }
    }
}
