package monster.greyde.kachalochka.ui.account

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import monster.greyde.kachalochka.App
import monster.greyde.kachalochka.TestKoin
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runNavigationUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SignInGateTest {
    @Test
    fun the_web_shows_only_the_sign_in_screen_until_an_account_exists() {
        runNavigationUiTest(content = { TestKoin(FakeGym(), signInRequired = true) { App() } }) {
            onNodeWithTag("sign-in-google").assertExists()
            onNodeWithTag("start-visit").assertDoesNotExist()
        }
    }

    @Test
    fun android_reaches_the_home_screen_with_nobody_signed_in() {
        runNavigationUiTest(content = { TestKoin(FakeGym(), signInRequired = false) { App() } }) {
            onNodeWithTag("start-visit").assertExists()
            onNodeWithTag("home-sign-in").assertExists()
        }
    }
}
