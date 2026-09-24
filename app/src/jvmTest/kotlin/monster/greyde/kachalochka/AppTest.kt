package monster.greyde.kachalochka

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalTestApi::class)
class AppTest {
    private val gym = FakeGym()

    private fun runApp(assertions: ComposeUiTest.() -> Unit) =
        runNavigationUiTest(content = { TestKoin(gym) { App() } }, assertions = assertions)

    @Test
    fun the_app_opens_on_home_with_the_sections_disabled() =
        runApp {
            onNodeWithTag("top-bar-title").assertTextEquals("Качалочка")
            onNodeWithTag("top-bar-back").assertDoesNotExist()
            onNodeWithTag("section-plans").assertIsNotEnabled()
            onNodeWithTag("section-stats").assertIsNotEnabled()
            onNodeWithTag("section-friends").assertIsNotEnabled()
        }

    @Test
    fun home_navigates_to_settings_and_back() =
        runApp {
            onNodeWithTag("account-avatar").performClick()
            onNodeWithTag("account-settings").performClick()
            waitForIdle()
            onNodeWithTag("settings-title").assertIsDisplayed()
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("start-visit").assertIsDisplayed()
        }

    @Test
    fun a_visit_started_at_home_can_be_ended_back_to_home() =
        runApp {
            onNodeWithTag("start-visit").performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визит")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("visit-counts").assertTextEquals("0 тренажёров · 0 подходов")
            onNodeWithTag("continue-visit").performClick()
            waitForIdle()
            onNodeWithTag("end-visit").performClick()
            waitForIdle()
            onNodeWithTag("start-visit").assertIsDisplayed()
        }

    @Test
    fun picking_a_machine_from_the_visit_fills_the_sheet() {
        val press = Machine.new("Жим ногами", null, gym.clock.current)
        runBlocking { gym.machines.upsert(press) }
        runApp {
            onNodeWithTag("start-visit").performClick()
            waitForIdle()
            onNodeWithTag("pick-machine").performClick()
            waitForIdle()
            onNodeWithTag("machine-row-${press.id.value}").performClick()
            waitForIdle()
            onNodeWithTag(
                "sheet-machine-name",
                useUnmergedTree = true,
            ).assertTextEquals("Жим ногами")
        }
    }

    @Test
    fun a_machine_created_from_the_picker_lands_in_the_visit_sheet() =
        runApp {
            onNodeWithTag("start-visit").performClick()
            waitForIdle()
            onNodeWithTag("pick-machine").performClick()
            waitForIdle()
            onNodeWithTag("machine-search").performTextInput("Гакк")
            waitForIdle()
            onNodeWithTag("create-machine").performClick()
            waitForIdle()
            onNodeWithTag("machine-name").assertTextContains("Гакк")
            onNodeWithTag("save-machine").performClick()
            waitForIdle()

            onNodeWithTag(
                "sheet-machine-name",
                useUnmergedTree = true,
            ).assertTextEquals("Гакк")
        }

    @Test
    fun home_opens_the_visit_calendar_and_a_past_visit_from_it() {
        val now = gym.clock.current
        val past = Visit(VisitId.random(), null, now - 2.days, now - 2.days, now, false)
        runBlocking { gym.visits.upsert(past) }
        runApp {
            onNodeWithTag("section-visits").performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визиты")
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("calendar-visit-${past.id.value}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визит · 12 ноября")

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визиты")
        }
    }

    @Test
    fun the_calendar_shows_a_visit_recorded_while_another_was_open() {
        val now = gym.clock.current
        val past = Visit(VisitId.random(), null, now - 2.days, now - 2.days, now, false)
        val hourLater = now - 2.days + 1.hours
        val later = Visit(VisitId.random(), null, hourLater, hourLater, now, false)
        runBlocking { gym.visits.upsert(past) }
        runApp {
            onNodeWithTag("section-visits").performClick()
            waitForIdle()
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("calendar-visit-${past.id.value}").performScrollTo().performClick()
            waitForIdle()
            runBlocking { gym.visits.upsert(later) }

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithTag("calendar-visit-${later.id.value}").performScrollTo().assertIsDisplayed()
        }
    }
}
