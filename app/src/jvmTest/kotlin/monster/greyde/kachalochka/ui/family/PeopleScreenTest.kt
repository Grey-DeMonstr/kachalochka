package monster.greyde.kachalochka.ui.family

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInEnglish
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PeopleScreenTest {
    private val gym = signedInGym()

    @Test
    fun the_screen_groups_children_and_parents_and_hides_an_empty_group() {
        gym.family.link(SASHA, IVAN_MEMBER)
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Люди")
            onNodeWithTag("people-children").assertExists()
            onNodeWithText("Саша").assertExists()
            onNodeWithTag("people-guardians").assertDoesNotExist()
        }
    }

    @Test
    fun with_nobody_linked_the_screen_says_so() =
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("people-empty").assertTextEquals("Пока никого нет")
        }

    @Test
    fun adding_a_child_shows_a_new_code_with_how_to_use_it() =
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("people-add").performClick()
            waitForIdle()
            onNodeWithTag("add-child").performClick()
            waitForIdle()

            onNodeWithTag("offer-code").assertTextEquals("Код для ребёнка: PAPA2345")
            onNodeWithTag("offer-code-link")
                .assertTextEquals("https://example.test/kachalochka/?parent=PAPA2345")
            onNodeWithTag("offer-code-hint").assertExists()
            onNodeWithTag("offer-code-share").assertExists()

            onNodeWithTag("offer-code-close").performClick()
            waitForIdle()
            onNodeWithTag("offer-code").assertDoesNotExist()
        }

    @Test
    fun without_a_page_address_the_code_comes_without_a_link() {
        gym.invites.pageAddress = null
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("people-add").performClick()
            waitForIdle()
            onNodeWithTag("add-child").performClick()
            waitForIdle()

            onNodeWithTag("offer-code").assertExists()
            onNodeWithTag("offer-code-link").assertDoesNotExist()
        }
    }

    @Test
    fun a_parent_is_added_by_code_after_reading_what_they_can_do() {
        gym.family.offered("PAPA2345", PAPA)
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("people-add").performClick()
            waitForIdle()
            onNodeWithTag("add-guardian").performClick()
            waitForIdle()
            onNodeWithTag("entry-rights").assertExists()
            onNodeWithTag("entry-link").assertIsNotEnabled()

            onNodeWithTag("entry-code-field").performTextInput("PAPA2345")
            onNodeWithTag("entry-link").performClick()
            waitForIdle()

            onNodeWithTag("entry-code-field").assertDoesNotExist()
            onNodeWithText("Папа").assertExists()
        }
    }

    @Test
    fun an_unknown_code_is_reported_under_the_field() =
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("people-add").performClick()
            waitForIdle()
            onNodeWithTag("add-guardian").performClick()
            waitForIdle()
            onNodeWithTag("entry-code-field").performTextInput("ZZZZ2345")
            onNodeWithTag("entry-link").performClick()
            waitForIdle()

            onNodeWithTag("entry-code-error").assertTextEquals("Код не найден или устарел")
        }

    @Test
    fun removing_a_child_goes_through_its_menu_and_a_question() {
        gym.family.link(SASHA, IVAN_MEMBER)
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("person-menu-${SASHA.userId.value}").performClick()
            waitForIdle()
            onNodeWithTag("person-remove-${SASHA.userId.value}").performClick()
            waitForIdle()
            onNodeWithTag("person-remove-confirm").performClick()
            waitForIdle()

            onNodeWithTag("people-empty").assertExists()
        }
        assertTrue(gym.family.links.isEmpty())
    }

    @Test
    fun trainers_and_trainees_come_first_in_their_own_groups() {
        gym.family.coach(SASHA, IVAN_MEMBER)
        gym.family.coach(IVAN_MEMBER, PAPA)
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("people-trainers").assertExists()
            onNodeWithTag("people-trainees").assertExists()
            onNodeWithTag("people-children").assertDoesNotExist()
        }
    }

    @Test
    fun adding_a_trainee_shows_a_trainer_code() =
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("people-add").performClick()
            waitForIdle()
            onNodeWithTag("add-trainee").performClick()
            waitForIdle()

            onNodeWithTag("offer-code").assertTextEquals("Код для ученика: TREN2345")
            onNodeWithTag("offer-code-link")
                .assertTextEquals("https://example.test/kachalochka/?trainer=TREN2345")
        }

    @Test
    fun a_trainer_is_added_by_code_after_reading_what_they_can_do() {
        gym.family.offeredCoaching("TREN2345", PAPA)
        runScreenTest(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("people-add").performClick()
            waitForIdle()
            onNodeWithTag("add-trainer").performClick()
            waitForIdle()
            onNodeWithTag("entry-rights").assertExists()

            onNodeWithTag("entry-code-field").performTextInput("TREN2345")
            onNodeWithTag("entry-link").performClick()
            waitForIdle()

            onNodeWithTag("people-trainers").assertExists()
        }
        assertTrue(gym.family.coaching.isNotEmpty())
    }

    @Test
    fun the_screen_speaks_english() =
        runScreenTestInEnglish(gym, screen = { PeopleScreen(onBack = {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("People")
            onNodeWithTag("people-empty").assertTextEquals("Nobody here yet")
        }
}
