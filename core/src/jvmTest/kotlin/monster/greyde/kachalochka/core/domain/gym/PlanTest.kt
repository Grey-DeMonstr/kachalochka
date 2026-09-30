package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class PlanTest {
    private val squat = MachineId("0f000000-0000-4000-8000-00000000000f")

    @Test
    fun a_plan_id_is_a_v4_uuid() {
        assertFailsWith<IllegalArgumentException> { PlanId("plan") }
        assertEquals(36, PlanId.random().value.length)
    }

    @Test
    fun plans_run_oldest_first_and_a_tie_goes_by_id() {
        val a =
            Plan(
                PlanId("0a000000-0000-4000-8000-00000000000a"),
                null,
                "",
                emptyList(),
                T0,
                T0,
                false,
            )
        val b = a.copy(id = PlanId("0b000000-0000-4000-8000-00000000000b"))
        val older = a.copy(id = PlanId.random(), createdAt = T0 - 1.seconds)

        assertEquals(listOf(older, a, b), listOf(b, a, older).sortedWith(planOrder))
    }

    @Test
    fun a_started_plan_appends_its_live_machines_in_plan_order() {
        val started =
            startedPlanned(emptyList(), emptyList(), listOf(ROW, PRESS), setOf(PRESS, ROW))

        assertEquals(listOf(ROW, PRESS), started)
    }

    @Test
    fun a_started_plan_skips_machines_already_recorded_or_planned_and_gone_ones() {
        val started =
            startedPlanned(
                planned = listOf(ROW),
                recorded = listOf(PRESS),
                plan = listOf(PRESS, ROW, squat, squat),
                live = setOf(PRESS, ROW),
            )

        assertEquals(listOf(ROW), started)
    }

    @Test
    fun only_live_planned_machines_without_a_set_show_as_planned() {
        val sets = listOf(set(VISIT_A, 60.0, atSeconds = 0, machine = PRESS))

        assertEquals(
            listOf(ROW),
            plannedWithoutSets(listOf(PRESS, ROW, squat, ROW), sets, setOf(PRESS, ROW)),
        )
    }
}
