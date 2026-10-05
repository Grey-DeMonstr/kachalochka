package monster.greyde.kachalochka.ui.family

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GuardiansScreenTest {
    private val gym = signedInGym()

    @Test
    fun the_screen_says_what_a_parent_can_do_and_links_by_code() {
        gym.family.offered("PAPA2345", PAPA)
        runScreenTest(gym, screen = { GuardiansScreen(onBack = {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Родители")
            onNodeWithTag("guardian-rights").assertExists()
            onNodeWithTag("guardian-empty").assertExists()
            onNodeWithTag("add-guardian").assertIsNotEnabled()

            onNodeWithTag("guardian-code-field").performTextInput("PAPA2345")
            onNodeWithTag("add-guardian").performClick()
            waitForIdle()

            onNodeWithText("Папа").assertExists()
        }
    }

    @Test
    fun an_unknown_code_is_reported_under_the_field() {
        runScreenTest(gym, screen = { GuardiansScreen(onBack = {}) }) {
            onNodeWithTag("guardian-code-field").performTextInput("ZZZZ2345")
            onNodeWithTag("add-guardian").performClick()
            waitForIdle()

            onNodeWithTag("guardian-code-error").assertTextEquals("Код не найден или устарел")
        }
    }
}
