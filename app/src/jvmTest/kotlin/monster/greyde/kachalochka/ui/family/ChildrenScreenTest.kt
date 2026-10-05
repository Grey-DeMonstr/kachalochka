package monster.greyde.kachalochka.ui.family

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInEnglish
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ChildrenScreenTest {
    private val gym = signedInGym()

    @Test
    fun the_screen_lists_the_children_and_shows_a_new_code_with_how_to_use_it() {
        gym.family.link(SASHA, IVAN_MEMBER)
        runScreenTest(gym, screen = { ChildrenScreen(onBack = {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Дети")
            onNodeWithText("Саша").assertExists()

            onNodeWithTag("add-child").performClick()
            waitForIdle()

            onNodeWithTag("child-code").assertTextEquals("Код для ребёнка: PAPA2345")
            onNodeWithTag("child-code-link")
                .assertTextEquals("https://example.test/kachalochka/?parent=PAPA2345")
            onNodeWithTag("child-code-hint").assertExists()
            onNodeWithTag("child-code-share").assertExists()
        }
    }

    @Test
    fun without_a_page_address_the_code_comes_without_a_link() {
        gym.invites.pageAddress = null
        runScreenTest(gym, screen = { ChildrenScreen(onBack = {}) }) {
            onNodeWithTag("add-child").performClick()
            waitForIdle()

            onNodeWithTag("child-code").assertExists()
            onNodeWithTag("child-code-link").assertDoesNotExist()
        }
    }

    @Test
    fun removing_a_child_goes_through_a_question() {
        gym.family.link(SASHA, IVAN_MEMBER)
        runScreenTest(gym, screen = { ChildrenScreen(onBack = {}) }) {
            onNodeWithTag("child-remove-${SASHA.userId.value}").performClick()
            onNodeWithTag("child-remove-confirm").performClick()
            waitForIdle()

            onNodeWithTag("child-empty").assertExists()
        }
        assertTrue(gym.family.links.isEmpty())
    }

    @Test
    fun the_screen_speaks_english() =
        runScreenTestInEnglish(gym, screen = { ChildrenScreen(onBack = {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Children")
            onNodeWithTag("child-empty").assertTextEquals("No children yet")
        }
}
