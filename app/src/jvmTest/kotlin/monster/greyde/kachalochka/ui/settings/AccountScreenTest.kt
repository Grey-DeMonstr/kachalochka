package monster.greyde.kachalochka.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInEnglish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class AccountScreenTest {
    private val ivan =
        session("11111111-1111-4111-8111-111111111111", "Иван", Instant.fromEpochSeconds(0))

    @Test
    fun the_screen_shows_the_email_and_how_the_account_signs_in() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(gym, screen = { AccountScreen(onBack = {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Аккаунт")
            onNodeWithTag("account-email", useUnmergedTree = true)
                .assertTextEquals("Иван@example.test")
        }
    }

    @Test
    fun signing_out_closes_the_screen() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        var backs = 0
        runScreenTest(gym, screen = { AccountScreen(onBack = { backs++ }) }) {
            onNodeWithTag("settings-sign-out").performClick()
            waitForIdle()
        }
        assertTrue(
            gym.accounts.accounts.value
                .isEmpty(),
        )
        assertEquals(1, backs)
    }

    @Test
    fun the_account_is_deleted_after_a_confirmation() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        var backs = 0
        runScreenTest(gym, screen = { AccountScreen(onBack = { backs++ }) }) {
            onNodeWithTag("delete-account").performClick()
            waitForIdle()
            onNodeWithTag("cancel-delete-account").performClick()
            waitForIdle()
            assertEquals(emptyList(), gym.accountServer.deleted)

            onNodeWithTag("delete-account").performClick()
            waitForIdle()
            onNodeWithTag("confirm-delete-account").assertIsNotEnabled()
            onNodeWithTag("delete-word").performTextInput("delete")
            onNodeWithTag("confirm-delete-account").performClick()
            waitForIdle()
        }
        assertEquals(listOf(ivan.account.userId), gym.accountServer.deleted)
        assertEquals(1, backs)
    }

    @Test
    fun the_screen_speaks_english() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTestInEnglish(gym, screen = { AccountScreen(onBack = {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Account")
            onNodeWithTag("settings-sign-out-label", useUnmergedTree = true)
                .assertTextEquals("Sign out")
        }
    }
}
