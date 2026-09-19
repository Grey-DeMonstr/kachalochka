package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
            onNodeWithTag("unit-custom").assertIsNotEnabled()
            onNodeWithTag("per-limb").assertIsNotEnabled()
            onNodeWithTag("mode-total").assertIsSelected()
            onNodeWithTag("step-2.5").assertIsSelected()

            onNodeWithTag("machine-name").performTextInput("Гакк-машина")
            onNodeWithTag("step-5").performClick()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()

            assertEquals(1, saved.size)
        }
    }
}
