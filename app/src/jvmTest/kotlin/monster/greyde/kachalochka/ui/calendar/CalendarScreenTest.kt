package monster.greyde.kachalochka.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalTestApi::class)
class CalendarScreenTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val sunday = Visit(VisitId.random(), null, t0 - 2.days, t0 - 2.days, t0, false)

    init {
        runBlocking { gym.visits.upsert(sunday) }
    }

    @Composable
    private fun calendar(
        onBack: () -> Unit = {},
        onOpenVisit: (VisitId) -> Unit = {},
    ) = CalendarScreen(onBack = onBack, onOpenSettings = {}, onOpenVisit = onOpenVisit)

    @Test
    fun a_marked_day_lists_its_visit_and_opens_it() {
        var opened: VisitId? = null
        runScreenTest(gym, screen = { calendar(onOpenVisit = { opened = it }) }) {
            onNodeWithTag("calendar-month").assertTextEquals("Ноябрь 2023")
            onNodeWithTag("day-2023-11-15").assertIsNotEnabled()
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("calendar-visit-${sunday.id.value}").performScrollTo().performClick()
            waitForIdle()
            assertEquals(sunday.id, opened)
        }
    }

    @Test
    fun removing_asks_for_confirmation_first() {
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("remove-visit-${sunday.id.value}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("cancel-remove").performClick()
            waitForIdle()
            assertEquals(
                false,
                gym.visits.rows
                    .getValue(sunday.id)
                    .deleted,
            )

            onNodeWithTag("remove-visit-${sunday.id.value}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("confirm-remove").performClick()
            waitForIdle()
            assertEquals(
                true,
                gym.visits.rows
                    .getValue(sunday.id)
                    .deleted,
            )
            onNodeWithTag("calendar-empty").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun a_visit_moves_to_the_day_tapped_after_move() {
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("move-visit-${sunday.id.value}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("move-banner").assertIsDisplayed()

            onNodeWithTag("day-2023-11-05").performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag("move-banner").assertDoesNotExist()
            val moved = assertNotNull(gym.visits.rows[sunday.id])
            assertEquals(CalendarDay(2023, 11, 5), CalendarDay.of(moved.recordedAt, Duration.ZERO))
        }
    }

    @Test
    fun a_move_offers_no_other_edits_and_can_be_cancelled() {
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("move-visit-${sunday.id.value}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("move-visit-${sunday.id.value}").assertDoesNotExist()
            onNodeWithTag("remove-visit-${sunday.id.value}").assertDoesNotExist()

            onNodeWithTag("cancel-move").performClick()
            waitForIdle()

            onNodeWithTag("move-banner").assertDoesNotExist()
            onNodeWithTag("remove-visit-${sunday.id.value}").performScrollTo().assertIsDisplayed()
            assertEquals(sunday, gym.visits.rows[sunday.id])
        }
    }

    @Test
    fun back_while_moving_cancels_the_move() {
        var backs = 0
        runScreenTest(gym, screen = { calendar(onBack = { backs++ }) }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("move-visit-${sunday.id.value}").performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithTag("move-banner").assertDoesNotExist()
            assertEquals(0, backs)
        }
    }

    @Test
    fun a_past_day_offers_to_add_a_visit_and_opens_it() {
        var opened: VisitId? = null
        runScreenTest(gym, screen = { calendar(onOpenVisit = { opened = it }) }) {
            onNodeWithTag("day-2023-11-10").performClick()
            waitForIdle()
            onNodeWithTag("add-visit").performScrollTo().assertTextEquals("Добавить визит")
            onNodeWithTag("add-visit").performClick()
            waitForIdle()
            assertNotNull(opened)
        }
    }
}
