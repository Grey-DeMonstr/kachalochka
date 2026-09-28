package monster.greyde.kachalochka.core.domain.friends

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendColorsTest {
    private val friends =
        (1..8).map { UserId("${it.toString().repeat(8)}-0000-4000-8000-000000000000") }
    private val oleg = friends[0]
    private val pasha = friends[1]
    private val anna = friends[2]
    private val misha = friends[3]

    @Test
    fun existing_colours_are_kept_unchanged() {
        val existing = mapOf(oleg to 5, pasha to 2)

        val colors = assignedColors(existing, listOf(oleg, pasha), FRIEND_PALETTE_SIZE, Random(1))

        assertEquals(existing, colors)
    }

    @Test
    fun a_new_friend_gets_a_palette_index() {
        repeat(20) { seed ->
            val colors =
                assignedColors(emptyMap(), listOf(oleg), FRIEND_PALETTE_SIZE, Random(seed))

            assertTrue(colors.getValue(oleg) in 0 until FRIEND_PALETTE_SIZE)
        }
    }

    @Test
    fun eight_friends_never_share_a_colour() {
        repeat(20) { seed ->
            val colors = assignedColors(emptyMap(), friends, FRIEND_PALETTE_SIZE, Random(seed))

            assertEquals(8, colors.values.toSet().size)
        }
    }

    @Test
    fun a_new_friend_takes_one_of_the_least_used_colours() {
        val existing = mapOf(oleg to 0, pasha to 1, anna to 2)
        repeat(20) { seed ->
            val colors = assignedColors(existing, listOf(misha), FRIEND_PALETTE_SIZE, Random(seed))

            assertTrue(colors.getValue(misha) !in 0..2)
        }
    }

    @Test
    fun a_colour_outside_the_palette_is_drawn_again() {
        val colors = assignedColors(mapOf(oleg to 8, pasha to -1), listOf(oleg), 8, Random(1))

        assertEquals(setOf(oleg), colors.keys)
        assertTrue(colors.getValue(oleg) in 0 until 8)
    }

    @Test
    fun a_friend_listed_twice_is_drawn_once_and_one_with_a_colour_never_again() {
        val colors = assignedColors(mapOf(oleg to 3), listOf(oleg, pasha, pasha), 8, Random(1))

        assertEquals(3, colors.getValue(oleg))
        assertEquals(setOf(oleg, pasha), colors.keys)
    }

    @Test
    fun the_same_seed_draws_the_same_colours() {
        assertEquals(
            assignedColors(emptyMap(), friends.take(3), FRIEND_PALETTE_SIZE, Random(1)),
            assignedColors(emptyMap(), friends.take(3), FRIEND_PALETTE_SIZE, Random(1)),
        )
    }
}
