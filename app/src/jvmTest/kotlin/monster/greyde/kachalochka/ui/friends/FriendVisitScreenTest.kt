package monster.greyde.kachalochka.ui.friends

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.MachineId
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
            FriendVisitScreen(OLEG.userId, "Олег", fixture.yesterday, {}, {}, {})
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Олег")
            onNodeWithTag("friend-visit-day").assertTextEquals("Понедельник, 13 ноября")
            onNodeWithTag("friend-group-${fixture.olegPress.id.value}").assertIsDisplayed()
            val first = gym.friends.sets.first()
            onNodeWithTag("friend-set-${first.id.value}").assertDoesNotExist()
            onNodeWithTag("pick-machine").assertDoesNotExist()
            onNodeWithTag("reorder-toggle").assertDoesNotExist()
            onNodeWithTag("set-sheet").assertDoesNotExist()
            // One load on mount: visits, sets, machines, the group's machines, links and photos,
            // and the mates, each a network round trip.
            assertEquals(7, gym.friends.reads)
        }
    }

    @Test
    fun tapping_a_machine_opens_the_friend_s_machine() {
        val opened = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            FriendVisitScreen(OLEG.userId, "Олег", fixture.yesterday, {}, {}, { opened += it })
        }) {
            onNodeWithTag("friend-group-${fixture.olegPress.id.value}").performClick()
            waitForIdle()
        }
        assertEquals(listOf(fixture.olegPress.id), opened)
    }

    @Test
    fun a_friend_s_machine_shows_its_results_as_an_own_row_does() {
        runScreenTest(gym, screen = {
            FriendVisitScreen(OLEG.userId, "Олег", fixture.yesterday, {}, {}, {})
        }) {
            onNodeWithTag("group-summary-${fixture.olegPress.id.value}")
                .assertTextEquals("80-85кг", "8-6")
        }
    }

    @Test
    fun offline_it_offers_a_retry() {
        gym.friends.offline = true
        runScreenTest(gym, screen = {
            FriendVisitScreen(OLEG.userId, "Олег", fixture.yesterday, {}, {}, {})
        }) {
            onNodeWithTag("friends-offline").assertTextEquals("Нет связи с сервером")
            gym.friends.offline = false
            onNodeWithTag("friends-retry").performClick()
            waitForIdle()
            onNodeWithTag("friends-offline").assertDoesNotExist()
        }
    }
}
