package monster.greyde.kachalochka.ui.friends

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupScreenTest {
    private val gym = signedInGym()

    @Test
    fun a_member_opens_their_calendar_but_the_viewer_s_own_row_does_not() {
        val group = gym.friends.group("Forest Street Gym", owner = OLEG, ME)
        val opened = mutableListOf<Friend>()
        runScreenTest(gym, screen = {
            GroupScreen(group.id, {}, {}, onOpenMember = { opened += it }, onGone = {})
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Forest Street Gym")
            onNodeWithTag("member-owner-${OLEG.userId.value}", useUnmergedTree = true)
                .assertTextEquals("owner")
            onNodeWithTag("invite-code").assertTextEquals("Invite code: ABCD2345")
            onNodeWithTag("member-${OLEG.userId.value}").performClick()
            onNodeWithTag("member-${ME.userId.value}").performClick()
            waitForIdle()
            // One load on mount: the group, its members and the group mates the colours are drawn
            // among, each a would-be network round trip.
            assertEquals(3, gym.friends.reads)
        }
        assertEquals(listOf(OLEG), opened)
    }

    @Test
    fun a_member_s_colour_is_picked_from_the_palette() {
        val group = gym.friends.group("Forest Street Gym", owner = OLEG, ME)
        runScreenTest(gym, screen = { GroupScreen(group.id, {}, {}, {}, {}) }) {
            onNodeWithTag("member-color-${ME.userId.value}").assertDoesNotExist()
            onNodeWithTag("member-color-${OLEG.userId.value}").performClick()
            waitForIdle()
            onNodeWithTag("color-picker").assertIsDisplayed()
            (0 until 8).forEach { onNodeWithTag("color-option-$it").assertIsDisplayed() }

            onNodeWithTag("color-option-6").performClick()
            waitForIdle()

            onNodeWithTag("color-picker").assertDoesNotExist()
            onNodeWithTag("member-color-${OLEG.userId.value}")
                .assertContentDescriptionEquals("Colour 7")
        }
    }

    @Test
    fun leaving_asks_first_and_then_closes_the_group() {
        val group = gym.friends.group("Forest Street Gym", owner = OLEG, ME)
        var gone = 0
        runScreenTest(gym, screen = {
            GroupScreen(group.id, {}, {}, onOpenMember = {}, onGone = { gone++ })
        }) {
            onNodeWithTag("delete-group").assertDoesNotExist()
            onNodeWithTag("leave-group").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("group-confirm").performClick()
            waitForIdle()
        }
        assertEquals(1, gone)
    }

    @Test
    fun the_owner_is_offered_deleting_instead_of_leaving() {
        val group = gym.friends.group("Forest Street Gym", owner = ME, OLEG)
        runScreenTest(gym, screen = { GroupScreen(group.id, {}, {}, {}, {}) }) {
            onNodeWithTag("leave-group").assertDoesNotExist()
            onNodeWithTag("delete-group").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun inviting_shows_the_confirmation() {
        val group = gym.friends.group("Forest Street Gym", owner = OLEG, ME)
        runScreenTest(gym, screen = { GroupScreen(group.id, {}, {}, {}, {}) }) {
            onNodeWithTag("invite").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("group-notice").assertTextEquals("Link copied")
        }
    }

    @Test
    fun offline_it_offers_a_retry() {
        val group = gym.friends.group("Forest Street Gym", owner = OLEG, ME)
        gym.friends.offline = true
        runScreenTest(gym, screen = { GroupScreen(group.id, {}, {}, {}, {}) }) {
            onNodeWithTag("friends-offline").assertTextEquals("No connection to the server")
            gym.friends.offline = false
            onNodeWithTag("friends-retry").performClick()
            waitForIdle()
            onNodeWithTag("friends-offline").assertDoesNotExist()
        }
    }
}
