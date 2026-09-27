package monster.greyde.kachalochka.ui.friends

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FriendVisitScreenTest {
    private val gym = signedInGym()
    private val fixture = OlegVisitFixture(gym)

    @BeforeTest
    fun setUp() = runBlocking { fixture.install() }

    @Test
    fun a_friend_s_visit_is_read_only() {
        runScreenTest(gym, screen = {
            FriendVisitScreen(OLEG.userId, "Олег", fixture.yesterday, {}, {})
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Олег · 13 ноября")
            onNodeWithTag("friend-group-${fixture.olegPress.id.value}").assertIsDisplayed()
            val first = gym.friends.sets.first()
            onNodeWithTag("friend-set-${first.id.value}").assertIsDisplayed()
            onNodeWithTag("pick-machine").assertDoesNotExist()
            onNodeWithTag("reorder-toggle").assertDoesNotExist()
            onNodeWithTag("set-sheet").assertDoesNotExist()
            // One load on mount: visits, sets and machines, each a would-be network round trip.
            assertEquals(3, gym.friends.reads)
        }
    }

    @Test
    fun offline_it_offers_a_retry() {
        gym.friends.offline = true
        runScreenTest(gym, screen = {
            FriendVisitScreen(OLEG.userId, "Олег", fixture.yesterday, {}, {})
        }) {
            onNodeWithTag("friends-offline").assertTextEquals("Нет связи с сервером")
            gym.friends.offline = false
            onNodeWithTag("friends-retry").performClick()
            waitForIdle()
            onNodeWithTag("friends-offline").assertDoesNotExist()
        }
    }
}
