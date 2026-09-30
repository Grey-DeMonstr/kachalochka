package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PlanWireTest {
    private val press = MachineId("0d000000-0000-4000-8000-00000000000d")
    private val row = MachineId("0e000000-0000-4000-8000-00000000000e")
    private val at = Instant.fromEpochMilliseconds(1_700_000_000_123)
    private val plan =
        Plan(
            PlanId("9b1f0c3e-0000-4000-8000-000000000001"),
            UserId("9b1f0c3e-0000-4000-8000-000000000002"),
            "Ноги",
            listOf(row, press),
            at,
            at,
            false,
        )

    @Test
    fun a_plan_survives_the_wire_round_trip_with_its_machines_in_order() {
        assertEquals(plan, PlanRow.of(plan).toPlan())
        assertEquals("[\"${row.value}\",\"${press.value}\"]", PlanRow.of(plan).machineIds)
    }

    @Test
    fun unreadable_machine_ids_read_as_the_entries_that_can_be_read() {
        assertEquals(emptyList(), machineIdsOf("not json"))
        assertEquals(emptyList(), machineIdsOf("{}"))
        assertEquals(listOf(press), machineIdsOf("[\"${press.value}\", 7, \"press\", null]"))
        assertEquals(listOf(press), machineIdsOf("[\"${press.value}\", \"${press.value}\"]"))
    }

    @Test
    fun a_visit_carries_its_planned_machines_over_the_wire() {
        val visit =
            Visit(VisitId.random(), null, CalendarDay(2026, 9, 30), at, at, false, listOf(press))

        assertEquals(visit, VisitRow.of(visit).toVisit())
        assertEquals("[\"${press.value}\"]", VisitRow.of(visit).planned)
    }
}
