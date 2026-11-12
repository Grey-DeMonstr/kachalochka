package monster.greyde.kachalochka.ui.stats

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsPicturesTest {
    private fun section(size: Int) = List(size) { "r" }

    private fun sizes(
        limit: Int,
        vararg sections: Int,
    ) = pictureChunks(sections.map(::section), limit).map { it.size }

    @Test
    fun no_rows_give_no_picture() {
        assertEquals(emptyList(), sizes(15))
        assertEquals(emptyList(), sizes(15, 0))
    }

    @Test
    fun rows_that_fit_stay_on_one_picture() {
        assertEquals(listOf(15), sizes(15, 15))
    }

    @Test
    fun a_big_section_splits_into_near_equal_parts_with_the_bigger_first() {
        assertEquals(listOf(12, 11), sizes(15, 23))
        assertEquals(listOf(15, 15), sizes(15, 30))
        assertEquals(listOf(11, 10, 10), sizes(15, 31))
    }

    @Test
    fun adjacent_small_sections_join_while_they_fit() {
        assertEquals(listOf(14, 9), sizes(15, 4, 6, 4, 9))
        assertEquals(listOf(15, 1), sizes(15, 5, 10, 1))
    }

    @Test
    fun a_split_section_is_not_joined_with_its_neighbours() {
        assertEquals(listOf(3, 8, 7, 2), sizes(10, 3, 15, 2))
    }

    @Test
    fun the_rows_keep_their_order() {
        val chunks = pictureChunks(listOf(listOf(1, 2), listOf(3), listOf(4, 5, 6, 7, 8)), 4)

        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8)), chunks)
    }
}
