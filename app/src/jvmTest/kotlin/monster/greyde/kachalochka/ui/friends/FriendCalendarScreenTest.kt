package monster.greyde.kachalochka.ui.friends

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FriendCalendarScreenTest {
    private val gym = signedInGym()
    private val fixture = OlegVisitFixture(gym)

    @BeforeTest
    fun setUp() = runBlocking { fixture.install() }

    @Test
    fun a_marked_day_opens_the_friend_s_visit_and_nothing_can_be_edited() {
        var opened: CalendarDay? = null
        runScreenTest(gym, screen = {
            FriendCalendarScreen(OLEG.userId, "Олег", {}, {}, onOpenVisit = { opened = it })
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Олег")
            onNodeWithTag("calendar-empty").assertTextEquals("Нет визита")
            onNodeWithTag("add-visit").assertDoesNotExist()
            // One load on mount: visits, machines and links; today has no visit's sets to read.
            assertEquals(3, gym.friends.reads)
            onNodeWithTag("day-2023-11-13").performClick()
            waitForIdle()
            onNodeWithTag("friend-day-visit").performScrollTo().performClick()
            waitForIdle()
        }
        assertEquals(CalendarDay(2023, 11, 13), opened)
    }

    @Test
    fun offline_it_offers_a_retry() {
        gym.friends.offline = true
        runScreenTest(gym, screen = {
            FriendCalendarScreen(OLEG.userId, "Олег", {}, {}, {})
        }) {
            onNodeWithTag("friends-offline").assertTextEquals("Нет связи с сервером")
            gym.friends.offline = false
            onNodeWithTag("friends-retry").performClick()
            waitForIdle()
            onNodeWithTag("friends-offline").assertDoesNotExist()
            onNodeWithTag("calendar-empty").assertTextEquals("Нет визита")
        }
    }
}
