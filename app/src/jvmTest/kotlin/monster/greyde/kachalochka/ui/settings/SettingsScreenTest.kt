package monster.greyde.kachalochka.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
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
private fun settings(onBack: () -> Unit = {}) = SettingsScreen(onBack = onBack)

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
    fun a_typed_length_waits_for_apply() {
        runScreenTest(FakeGym(), screen = { settings() }) {
            onNodeWithTag("apply-settings").performScrollTo().assertIsNotEnabled()
            onNodeWithTag("transition-millis").performTextReplacement("0")

            onNodeWithTag("transition-millis").assertTextEquals("0")
            onNodeWithTag("apply-settings").performScrollTo().assertIsEnabled().performClick()
            onNodeWithTag("apply-settings").assertIsNotEnabled()
        }
    }

    @Test
    fun a_length_out_of_range_or_not_a_number_is_refused() {
        runScreenTest(FakeGym(), screen = { settings() }) {
            onNodeWithTag("transition-millis").performTextReplacement("1001")
            onNodeWithTag("transition-millis").performTextReplacement("abc")

            onNodeWithTag("transition-millis").assertTextEquals("150")
        }
    }

    @Test
    fun the_theme_is_chosen_like_the_weight_unit_and_waits_for_apply() {
        runScreenTest(FakeGym(), screen = { settings() }) {
            onNodeWithTag("theme-dark").performScrollTo().performClick()

            onNodeWithTag("theme-dark").assertIsSelected()
            onNodeWithTag("apply-settings").performScrollTo().assertIsEnabled()
        }
    }

    @Test
    fun leaving_with_changes_asks_and_can_drop_them() {
        var backs = 0
        runScreenTest(FakeGym(), screen = { settings(onBack = { backs++ }) }) {
            onNodeWithTag("theme-light").performScrollTo().performClick()
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            assertEquals(0, backs)

            onNodeWithTag("leave-discard").performClick()
            waitForIdle()
            assertEquals(1, backs)
        }
    }

    @Test
    fun signed_out_shows_the_body_fields_but_no_nickname() {
        runScreenTest(
            FakeGym(),
            screen = { settings() },
        ) {
            onNodeWithTag("nickname").assertDoesNotExist()
            onNodeWithTag("birth-date").assertExists()
            onNodeWithTag("height").assertExists()
            onNodeWithTag("apply-settings").assertIsNotEnabled()
        }
    }

    @Test
    fun choosing_the_body_fields_saves_them_to_the_profile() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { settings() }) {
            onNodeWithTag("sex-female").performClick()
            onNodeWithTag("birth-date").performTextInput("15.06.1990")
            onNodeWithTag("height").performTextInput("165")
            onNodeWithTag("apply-settings").performScrollTo().performClick()
            waitForIdle()
        }
        val profile = runBlocking { gym.profiles.forOwner(null) }
        assertEquals(Sex.Female, profile?.sex)
        assertEquals(CalendarDay(1990, 6, 15), profile?.birthDate)
        assertEquals(165.0, profile?.heightCm)
    }

    @Test
    fun choosing_a_weight_unit_saves_it_to_the_profile() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { settings() }) {
            onNodeWithTag("weight-unit-kg").assertExists()
            onNodeWithTag("weight-unit-mixed").assertExists()
            onNodeWithTag("weight-unit-lb").performScrollTo().performClick()
            onNodeWithTag("apply-settings").performScrollTo().performClick()
            waitForIdle()
        }
        val profile = runBlocking { gym.profiles.forOwner(null) }
        assertEquals(PreferredWeightUnit.Lb, profile?.weightUnit)
    }

    @Test
    fun signed_in_shows_the_field_and_a_disabled_save_button_until_it_changes() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(
            gym,
            screen = { settings() },
        ) {
            onNodeWithTag("nickname").assertExists()
            onNodeWithTag("apply-settings").assertIsNotEnabled()

            onNodeWithTag("nickname").performTextInput("Ванёк")

            onNodeWithTag("apply-settings").assertIsEnabled()
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
            onNodeWithTag("apply-settings").performScrollTo().performClick()

            onNodeWithTag("apply-settings").assertIsNotEnabled()
        }
    }

    @Test
    fun a_signed_in_account_is_deleted_after_a_confirmation() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(gym, screen = { settings() }) {
            onNodeWithTag("delete-account").assertDoesNotExist()
            onNodeWithTag("settings-advanced").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("delete-account").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("cancel-delete-account").performClick()
            waitForIdle()
            assertEquals(emptyList(), gym.accountServer.deleted)

            onNodeWithTag("delete-account").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("confirm-delete-account").assertIsNotEnabled()
            onNodeWithTag("delete-word").performTextInput("delete")
            onNodeWithTag("confirm-delete-account").performClick()
            waitForIdle()

            onNodeWithTag("delete-account").assertDoesNotExist()
        }
        assertEquals(listOf(ivan.account.userId), gym.accountServer.deleted)
    }

    @Test
    fun without_an_account_nothing_can_be_deleted() {
        runScreenTest(FakeGym(), screen = { settings() }) {
            onNodeWithTag("settings-advanced").assertDoesNotExist()
            onNodeWithTag("delete-account").assertDoesNotExist()
        }
    }
}
