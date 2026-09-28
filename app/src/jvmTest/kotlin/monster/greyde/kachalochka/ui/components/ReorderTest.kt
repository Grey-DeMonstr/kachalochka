package monster.greyde.kachalochka.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ReorderTest {
    private val even = listOf(100, 100, 100)

    @Test
    fun an_item_lands_where_its_centre_is() {
        assertEquals(0, dropIndex(even, 0, 0f))
        assertEquals(1, dropIndex(even, 0, 60f))
        assertEquals(2, dropIndex(even, 0, 160f))
        assertEquals(0, dropIndex(even, 2, -160f))
    }

    @Test
    fun a_drag_past_either_end_lands_on_that_end() {
        assertEquals(2, dropIndex(even, 0, 1000f))
        assertEquals(0, dropIndex(even, 2, -1000f))
    }

    @Test
    fun unequal_heights_are_measured_item_by_item() {
        assertEquals(1, dropIndex(listOf(50, 200, 50), 0, 100f))
    }

    @Test
    fun a_shorter_list_forgets_the_heights_of_removed_items() {
        val state = ReorderState()
        even.forEachIndexed { index, height -> state.heights[index] = height }
        state.dragging = 0
        state.dragY = 1000f

        state.retain(2)

        assertEquals(1, state.target())
    }
}
