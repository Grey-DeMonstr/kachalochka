package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.friends.FRIEND_PALETTE_SIZE
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours

class FriendColorStoreTest {
    private val gym = FakeGym()
    private val store = FriendColorStore(gym.profiles, gym.clock, Random(1))
    private val owner = ME.userId

    @Test
    fun the_first_colours_create_the_owner_s_profile() =
        runTest {
            val colors = store.colorsFor(owner, listOf(OLEG.userId, PASHA.userId))

            val profile = assertNotNull(gym.profiles.forOwner(owner))
            assertEquals(ProfileId(owner.value), profile.id)
            assertEquals(colors, profile.friendColors)
            assertEquals(setOf(OLEG.userId, PASHA.userId), colors.keys)
            assertEquals(2, colors.values.toSet().size)
            colors.values.forEach { assertEquals(true, it in 0 until FRIEND_PALETTE_SIZE) }
        }

    @Test
    fun known_friends_write_nothing() =
        runTest {
            val first = store.colorsFor(owner, listOf(OLEG.userId))
            val written = assertNotNull(gym.profiles.forOwner(owner)).updatedAt
            gym.clock.current += 1.hours

            assertEquals(first, store.colorsFor(owner, listOf(OLEG.userId)))
            assertEquals(written, gym.profiles.forOwner(owner)?.updatedAt)
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
