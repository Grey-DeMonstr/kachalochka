package monster.greyde.kachalochka.ui.account

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SignInScreenTest {
    @Test
    fun the_google_button_is_disabled_when_supabase_is_unconfigured() {
        val gym = FakeGym(credentials = SupabaseCredentials("", ""))
        runScreenTest(gym, screen = { SignInScreen(onSignIn = {}) }) {
            onNodeWithTag("sign-in-google").assertIsNotEnabled()
            onNodeWithTag("sign-in-unconfigured").assertExists()
        }
    }
}
