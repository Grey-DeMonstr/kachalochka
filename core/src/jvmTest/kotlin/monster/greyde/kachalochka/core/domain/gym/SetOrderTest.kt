package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class SetOrderTest {
    private val now = T0 + 1.hours

    private fun List<WorkoutSet>.after(changed: List<WorkoutSet>) =
        map { set -> changed.firstOrNull { it.id == set.id } ?: set }

    @Test
    fun a_new_set_goes_after_every_set_of_the_visit() {
        val first = set(VISIT_A, 60.0, atSeconds = 0)
        val moved = set(VISIT_A, 60.0, atSeconds = 1, position = 3)

        assertEquals(1, nextPosition(emptyList()))
        assertEquals(4, nextPosition(listOf(first, moved)))
    }

    @Test
    fun moving_a_machine_up_renumbers_the_visit_in_the_new_order() {
        val press1 = set(VISIT_A, 60.0, atSeconds = 0)
        val row1 = set(VISIT_A, 45.0, atSeconds = 1, machine = ROW)
        val press2 = set(VISIT_A, 70.0, atSeconds = 2)
        val sets = listOf(press1, row1, press2)

        val changed = machineMoved(sets, ROW, -1, now)

        assertEquals(
            listOf(1, 2, 3),
            listOf(row1, press1, press2).map { s -> changed.single { it.id == s.id }.position },
        )
        assertEquals(listOf(now, now, now), changed.map { it.updatedAt })
        assertEquals(listOf(ROW, PRESS), groupByMachine(sets.after(changed)).map { it.machineId })
    }

    @Test
    fun the_first_machine_does_not_move_up_nor_the_last_down() {
        val sets =
            listOf(
                set(VISIT_A, 60.0, atSeconds = 0),
                set(VISIT_A, 45.0, atSeconds = 1, machine = ROW),
            )

        assertEquals(emptyList(), machineMoved(sets, PRESS, -1, now))
        assertEquals(emptyList(), machineMoved(sets, ROW, +1, now))
    }

    @Test
    fun a_set_swaps_places_with_its_neighbour_on_the_same_machine() {
        val first = set(VISIT_A, 60.0, atSeconds = 0, position = 1)
        val row = set(VISIT_A, 45.0, atSeconds = 1, machine = ROW, position = 2)
        val second = set(VISIT_A, 70.0, atSeconds = 2, position = 3)

        val changed = setMoved(listOf(first, row, second), second.id, -1, now)

        assertEquals(
            setOf(first.id to 3, second.id to 1),
            changed.map { it.id to it.position }.toSet(),
        )
    }

    @Test
    fun a_set_among_sets_at_one_position_renumbers_the_visit_first() {
        val first = set(VISIT_A, 60.0, atSeconds = 0)
        val second = set(VISIT_A, 70.0, atSeconds = 1)
        val third = set(VISIT_A, 80.0, atSeconds = 2)
        val sets = listOf(first, second, third)

        val changed = setMoved(sets, first.id, +1, now)

        assertEquals(
            listOf(second.id, first.id, third.id),
            groupByMachine(sets.after(changed)).single().sets.map { it.id },
        )
        assertEquals(
            setOf(first.id to 2, second.id to 1, third.id to 3),
            changed.map { it.id to it.position }.toSet(),
        )
    }

    @Test
    fun a_set_moved_after_a_half_written_reorder_keeps_the_machine_order() {
        val first = set(VISIT_A, 60.0, atSeconds = 0, position = 2)
        val row = set(VISIT_A, 45.0, atSeconds = 1, machine = ROW, position = 2)
        val second = set(VISIT_A, 70.0, atSeconds = 2, position = 5)
        val sets = listOf(first, row, second)

        val groups = groupByMachine(sets.after(setMoved(sets, second.id, -1, now)))

        assertEquals(
            listOf(PRESS to listOf(second.id, first.id), ROW to listOf(row.id)),
            groups.map { group -> group.machineId to group.sets.map { it.id } },
        )
    }

    @Test
    fun a_machine_s_first_set_does_not_move_up() {
        val first = set(VISIT_A, 60.0, atSeconds = 0)

        assertEquals(emptyList(), setMoved(listOf(first), first.id, -1, now))
    }
}
