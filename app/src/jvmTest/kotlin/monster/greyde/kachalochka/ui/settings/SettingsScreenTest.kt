package monster.greyde.kachalochka.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private fun session(
    id: String,
    name: String,
) = AccountSession(
    Account(UserId(id), "$name@example.test", name),
    "access",
    "refresh",
    Instant.fromEpochSeconds(1_700_000_000),
)

@Composable
private fun settings(onTransitionMillisChange: (Int) -> Unit = {}) =
    SettingsScreen(
        mode = ThemeMode.Dark,
        onModeChange = {},
        transitionMillis = 150,
        onTransitionMillisChange = onTransitionMillisChange,
        onBack = {},
    )

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")

    @Test
    fun the_transition_field_shows_the_stored_length() {
        runScreenTest(FakeGym(), screen = { settings() }) {
            onNodeWithTag("transition-millis").assertTextEquals("150")
        }
    }

    @Test
    fun typing_a_length_reports_it() {
        val reported = mutableListOf<Int>()
        runScreenTest(FakeGym(), screen = { settings { reported += it } }) {
            onNodeWithTag("transition-millis").performTextReplacement("0")

            onNodeWithTag("transition-millis").assertTextEquals("0")
            assertEquals(listOf(0), reported)
        }
    }

    @Test
    fun a_length_out_of_range_or_not_a_number_is_refused() {
        val reported = mutableListOf<Int>()
        runScreenTest(FakeGym(), screen = { settings { reported += it } }) {
            onNodeWithTag("transition-millis").performTextReplacement("1001")
            onNodeWithTag("transition-millis").performTextReplacement("abc")

            onNodeWithTag("transition-millis").assertTextEquals("150")
            assertEquals(emptyList(), reported)
        }
    }

    @Test
    fun signed_out_shows_no_nickname_field() {
        runScreenTest(
            FakeGym(),
            screen = { settings() },
        ) {
            onNodeWithTag("nickname").assertDoesNotExist()
            onNodeWithTag("save-nickname").assertDoesNotExist()
        }
    }

    @Test
    fun signed_in_shows_the_field_and_a_disabled_save_button_until_it_changes() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(
            gym,
            screen = { settings() },
        ) {
            onNodeWithTag("nickname").assertExists()
            onNodeWithTag("save-nickname").assertIsNotEnabled()

            onNodeWithTag("nickname").performTextInput("Ванёк")

            onNodeWithTag("save-nickname").assertIsEnabled()
        }
    }

    @Test
    fun saving_writes_the_typed_nickname() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(
            gym,
            screen = { settings() },
        ) {
            onNodeWithTag("nickname").performTextInput("Ванёк")
            onNodeWithTag("save-nickname").performClick()

            onNodeWithTag("save-nickname").assertIsNotEnabled()
        }
    }
}
