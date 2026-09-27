package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MachineFormScreenTest {
    private val gym = FakeGym()

    @Test
    fun the_form_saves_with_defaults_and_shows_unbuilt_controls_disabled() {
        val saved = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, ""), {}, {}, onSaved = { saved += it })
        }) {
            onNodeWithTag("save-machine").assertIsNotEnabled()
            onNodeWithTag("machine-photo").assertIsNotEnabled()
            onNodeWithTag("per-limb").assertIsNotEnabled()
            onNodeWithTag("mode-total").assertIsSelected()
            onNodeWithTag("mode-counterweight").assertDoesNotExist()
            onNodeWithTag("step-2.5").assertIsSelected()

            onNodeWithTag("machine-name").performTextInput("Гакк-машина")
            onNodeWithTag("step-5").performClick()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()

            assertEquals(1, saved.size)
        }
    }

    @Test
    fun an_own_unit_asks_for_its_name_and_shows_it_by_the_platform_weight() {
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, "Гравитрон"), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("unit-label").assertDoesNotExist()

            onNodeWithTag("unit-custom").performClick()
            waitForIdle()
            onNodeWithTag("unit-custom").assertIsSelected()
            onNodeWithTag("save-machine").assertIsNotEnabled()

            onNodeWithTag("unit-label").performTextInput("очень длинная единица")
            waitForIdle()
            onNodeWithTag("unit-label").assertTextEquals("очень длинна")
            onNodeWithTag("platform-weight-unit", useUnmergedTree = true)
                .assertTextEquals("очень длинна")
            onNodeWithTag("save-machine").assertIsEnabled()
        }
    }

    @Test
    fun a_quick_step_fills_the_field_and_a_typed_step_is_saved() {
        val saved = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            MachineFormScreen(
                MachineFormArgs(null, null, "Гакк"),
                {},
                {},
                onSaved = { saved += it },
            )
        }) {
            onNodeWithTag("weight-step").assertTextEquals("2,5")
            // The chips sit below the fold; a real click needs them scrolled into view first.
            onNodeWithTag("step-5").performScrollTo()
            onNodeWithTag("step-5").performClick()
            waitForIdle()
            onNodeWithTag("weight-step").assertTextEquals("5")
            onNodeWithTag("step-5").assertIsSelected()

            onNodeWithTag("weight-step").performTextReplacement("1,25")
            waitForIdle()
            onNodeWithTag("step-5").assertIsNotSelected()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()
        }
        val machine = runBlocking { gym.machines.byId(saved.single()) }
        assertEquals(1.25, machine?.weightStep)
    }

    @Test
    fun a_form_opened_from_a_visit_says_where_the_machine_goes() =
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, ""), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("machine-visit-hint").assertExists()
        }

    @Test
    fun a_form_opened_from_the_list_has_no_visit_hint() =
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, ""), {}, {}, {}, inVisit = false)
        }) {
            onNodeWithTag("machine-visit-hint").assertDoesNotExist()
        }
}
