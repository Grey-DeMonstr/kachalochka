package monster.greyde.kachalochka

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.Test

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
            onNodeWithTag("open-settings").performClick()
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
            onNodeWithTag("top-bar-title").assertTextEquals("Визит · 0:00")
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
}
