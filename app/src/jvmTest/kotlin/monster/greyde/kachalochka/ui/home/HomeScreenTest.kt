package monster.greyde.kachalochka.ui.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.time.Instant

private fun session(
    id: String,
    name: String,
) = AccountSession(
    Account(UserId(id), "$name@example.test", name),
    "access-$name",
    "refresh-$name",
    Instant.fromEpochSeconds(1_700_000_000),
)

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Ivan")

    @Test
    fun friends_stay_locked_until_an_account_is_signed_in() =
        runScreenTest(FakeGym(), screen = { HomeScreen({}, {}, {}) }) {
            onNodeWithTag("section-friends-lock", useUnmergedTree = true).assertExists()
        }

    @Test
    fun friends_unlock_once_an_account_is_signed_in() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}) }) {
            onNodeWithTag("section-friends-lock", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun the_sign_in_button_stays_hidden_without_supabase_credentials() {
        val gym = FakeGym(credentials = SupabaseCredentials("", ""))
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}) }) {
            onNodeWithTag("home-sign-in").assertDoesNotExist()
        }
    }

    /** The fake sign-in has nothing queued to hand back, which is a refusal like any other. */
    @Test
    fun a_refused_sign_in_says_so_under_the_button() =
        runScreenTest(FakeGym(), screen = { HomeScreen({}, {}, {}) }) {
            onNodeWithTag("sign-in-failure").assertDoesNotExist()

            onNodeWithTag("home-sign-in").performClick()
            waitForIdle()

            onNodeWithTag("sign-in-failure").assertExists()
        }

    /** Credential Manager throws without the client id, so the button must not be offered. */
    @Test
    fun the_sign_in_button_stays_hidden_without_a_google_web_client_id() {
        val gym = FakeGym(credentials = SupabaseCredentials("https://example.test", "anon-key"))
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}) }) {
            onNodeWithTag("home-sign-in").assertDoesNotExist()
        }
    }
}
