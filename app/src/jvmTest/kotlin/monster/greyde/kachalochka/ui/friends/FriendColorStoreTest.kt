package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.friends.FRIEND_PALETTE_SIZE
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

class FriendColorStoreTest {
    private val gym = FakeGym()
    private val store = FriendColorStore(gym.profiles, gym.clock)
    private val owner = ME.userId
    private val vova = UserId("55555555-5555-4555-8555-555555555555")

    private suspend fun storedProfile() =
        Profile.new(owner, gym.clock.current).copy(displayName = "Ванёк").also {
            gym.profiles.upsert(it)
        }

    @Test
    fun without_a_profile_colours_are_drawn_but_nothing_is_written() =
        runTest {
            val colors = store.colorsFor(owner, listOf(OLEG.userId, PASHA.userId))

            assertNull(gym.profiles.forOwner(owner))
            assertEquals(setOf(OLEG.userId, PASHA.userId), colors.keys)
            assertEquals(2, colors.values.toSet().size)
            colors.values.forEach { assertEquals(true, it in 0 until FRIEND_PALETTE_SIZE) }
        }

    /**
     * Guaranteed: the same owner and the same set of friends give the same map, whatever the
     * order they are listed in or the store's own random source. Adding a friend may recolour
     * the others until a profile keeps the colours.
     */
    @Test
    fun without_a_profile_every_screen_draws_the_same_colours() =
        runTest {
            val friends = listOf(OLEG.userId, PASHA.userId)
            val first = FriendColorStore(gym.profiles, gym.clock)
            val second = FriendColorStore(gym.profiles, gym.clock)

            val colors = first.colorsFor(owner, friends)

            assertEquals(colors, first.colorsFor(owner, friends))
            assertEquals(colors, second.colorsFor(owner, friends.reversed()))
            assertNull(gym.profiles.forOwner(owner))
        }

    @Test
    fun colours_drawn_beside_an_existing_profile_are_not_written_into_it() =
        runTest {
            val profile = storedProfile()
            gym.clock.current += 1.hours

            val colors = store.colorsFor(owner, listOf(OLEG.userId, PASHA.userId))

            assertEquals(setOf(OLEG.userId, PASHA.userId), colors.keys)
            assertEquals(profile, gym.profiles.forOwner(owner))
        }

    @Test
    fun a_new_friend_beside_stored_colours_gets_the_same_colour_on_every_screen() =
        runTest {
            gym.profiles.upsert(
                Profile
                    .new(owner, gym.clock.current)
                    .copy(friendColors = mapOf(OLEG.userId to 3)),
            )
            val friends = listOf(OLEG.userId, PASHA.userId, vova)
            val first = FriendColorStore(gym.profiles, gym.clock)
            val second = FriendColorStore(gym.profiles, gym.clock)

            val colors = first.colorsFor(owner, friends)

            assertEquals(3, colors[OLEG.userId])
            assertEquals(colors, second.colorsFor(owner, friends.reversed()))
            assertEquals(mapOf(OLEG.userId to 3), gym.profiles.forOwner(owner)?.friendColors)
        }

    @Test
    fun a_chosen_colour_creates_the_profile_when_there_is_none() =
        runTest {
            store.set(owner, OLEG.userId, 2)

            val profile = assertNotNull(gym.profiles.forOwner(owner))
            assertEquals(ProfileId(owner.value), profile.id)
            assertEquals(mapOf(OLEG.userId to 2), profile.friendColors)
        }

    @Test
    fun only_the_asked_friends_come_back_while_the_rest_stay_stored() =
        runTest {
            val stored = mapOf(OLEG.userId to 3, PASHA.userId to 4)
            gym.profiles.upsert(
                Profile.new(owner, gym.clock.current).copy(friendColors = stored),
            )

            assertEquals(mapOf(OLEG.userId to 3), store.colorsFor(owner, listOf(OLEG.userId)))
            assertEquals(stored, gym.profiles.forOwner(owner)?.friendColors)
        }

    @Test
    fun a_chosen_colour_replaces_one_entry_and_keeps_the_profile() =
        runTest {
            val original =
                Profile
                    .new(owner, gym.clock.current)
                    .copy(displayName = "Ванёк", friendColors = mapOf(OLEG.userId to 1))
            gym.profiles.upsert(original)
            gym.clock.current += 1.hours

            store.set(owner, OLEG.userId, 6)

            val profile = assertNotNull(gym.profiles.forOwner(owner))
            assertEquals(mapOf(OLEG.userId to 6), profile.friendColors)
            assertEquals("Ванёк", profile.displayName)
            assertEquals(gym.clock.current, profile.updatedAt)
        }
}
