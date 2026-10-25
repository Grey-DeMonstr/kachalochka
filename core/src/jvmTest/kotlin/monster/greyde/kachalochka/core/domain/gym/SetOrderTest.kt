package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SetOrderTest {
    private val now = T0 + 1.hours
    private val curl = MachineId("0f000000-0000-4000-8000-00000000000f")

    private fun List<WorkoutSet>.after(changed: List<WorkoutSet>) =
        map { set -> changed.firstOrNull { it.id == set.id } ?: set }

    private fun threeMachines(): List<WorkoutSet> =
        listOf(
            set(VISIT_A, 60.0, atSeconds = 0, position = 1),
            set(VISIT_A, 45.0, atSeconds = 1, machine = ROW, position = 2),
            set(VISIT_A, 20.0, atSeconds = 2, machine = curl, position = 3),
            set(VISIT_A, 22.0, atSeconds = 3, machine = curl, position = 4),
        )

    @Test
    fun a_new_set_goes_after_every_set_of_the_visit() {
        val first = set(VISIT_A, 60.0, atSeconds = 0)
        val moved = set(VISIT_A, 60.0, atSeconds = 1, position = 3)

        assertEquals(1, nextPosition(emptyList()))
        assertEquals(4, nextPosition(listOf(first, moved)))
    }

    @Test
    fun the_last_machine_moved_to_the_top_goes_first_with_its_sets() {
        val sets = threeMachines()

        val changed = machineMovedTo(sets, curl, 0, now)

        assertEquals(
            listOf(curl, PRESS, ROW),
            groupByMachine(sets.after(changed)).map { it.machineId },
        )
        assertEquals(
            listOf(sets[2].id, sets[3].id),
            groupByMachine(sets.after(changed)).first().sets.map { it.id },
        )
    }

    @Test
    fun an_unknown_machine_or_one_moved_to_its_own_or_a_missing_place_writes_nothing() {
        val sets = threeMachines()

        assertEquals(emptyList(), machineMovedTo(sets, ROW, 1, now))
        assertEquals(emptyList(), machineMovedTo(sets, ROW, -1, now))
        assertEquals(emptyList(), machineMovedTo(sets, ROW, 3, now))
        assertEquals(emptyList(), machineMovedTo(sets, MachineId.random(), 0, now))
    }

    @Test
    fun only_the_sets_whose_position_changed_are_written_stamped_now() {
        val sets = threeMachines()

        val changed = machineMovedTo(sets, ROW, 0, now)

        assertEquals(
            setOf(sets[0].id to 2, sets[1].id to 1),
            changed.map { it.id to it.position }.toSet(),
        )
        assertTrue(changed.all { it.updatedAt == now })
    }
}
