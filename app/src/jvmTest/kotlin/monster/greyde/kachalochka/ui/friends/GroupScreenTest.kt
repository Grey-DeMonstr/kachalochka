package monster.greyde.kachalochka.ui.friends

import androidx.compose.ui.test.ExperimentalTestApi
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
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        val opened = mutableListOf<Friend>()
        runScreenTest(gym, screen = {
            GroupScreen(group.id, {}, {}, onOpenMember = { opened += it }, onGone = {})
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Зал на Лесной")
            onNodeWithTag("member-owner-${OLEG.userId.value}", useUnmergedTree = true)
                .assertTextEquals("владелец")
            onNodeWithTag("invite-code").assertTextEquals("Код приглашения: ABCD2345")
            onNodeWithTag("member-${OLEG.userId.value}").performClick()
            onNodeWithTag("member-${ME.userId.value}").performClick()
            waitForIdle()
            // One load on mount: the group and its members, each a would-be network round trip.
            assertEquals(2, gym.friends.reads)
        }
        assertEquals(listOf(OLEG), opened)
    }

    @Test
    fun leaving_asks_first_and_then_closes_the_group() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
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
        val group = gym.friends.group("Зал на Лесной", owner = ME, OLEG)
        runScreenTest(gym, screen = { GroupScreen(group.id, {}, {}, {}, {}) }) {
            onNodeWithTag("leave-group").assertDoesNotExist()
            onNodeWithTag("delete-group").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun inviting_shows_the_confirmation() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        runScreenTest(gym, screen = { GroupScreen(group.id, {}, {}, {}, {}) }) {
            onNodeWithTag("invite").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("group-notice").assertTextEquals("Ссылка скопирована")
        }
    }

    @Test
    fun offline_it_offers_a_retry() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        gym.friends.offline = true
        runScreenTest(gym, screen = { GroupScreen(group.id, {}, {}, {}, {}) }) {
            onNodeWithTag("friends-offline").assertTextEquals("Нет связи с сервером")
            gym.friends.offline = false
            onNodeWithTag("friends-retry").performClick()
            waitForIdle()
            onNodeWithTag("friends-offline").assertDoesNotExist()
        }
    }
}
