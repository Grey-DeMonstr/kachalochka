package monster.greyde.kachalochka.ui.account

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.home.HomeScreen
import monster.greyde.kachalochka.ui.settings.SettingsScreen
import monster.greyde.kachalochka.ui.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
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

private fun recordedSet(owner: UserId) =
    WorkoutSet(
        WorkoutSetId.random(),
        owner,
        VisitId.random(),
        MachineId.random(),
        40.0,
        10,
        Instant.fromEpochSeconds(1_700_000_000),
        Instant.fromEpochSeconds(1_700_000_000),
        false,
    )

@OptIn(ExperimentalTestApi::class)
class AccountMenuTest {
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Ivan")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Misha")

    @Test
    fun the_menu_lists_the_accounts_and_marks_the_active_one() {
        val gym = FakeGym().withAccounts(ivan, misha, active = misha)
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}) }) {
            onNodeWithTag("account-avatar").performClick()
            onNodeWithTag("account-11111111-1111-4111-8111-111111111111").assertExists()
            onNodeWithTag("account-22222222-2222-4222-8222-222222222222-active").assertExists()
            onNodeWithTag("account-add").assertExists()
            onNodeWithTag("account-settings").assertExists()
            onNodeWithTag("account-sign-out").assertTextContains("Misha", substring = true)
        }
    }

    @Test
    fun tapping_another_account_switches_to_it() {
        val gym = FakeGym().withAccounts(ivan, misha, active = misha)
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}) }) {
            onNodeWithTag("account-avatar").performClick()
            onNodeWithTag("account-11111111-1111-4111-8111-111111111111").performClick()
            onNodeWithTag("account-avatar").assertTextEquals("I")
        }
    }

    @Test
    fun the_avatar_is_an_outline_while_nobody_is_signed_in() {
        runScreenTest(FakeGym(), screen = { HomeScreen({}, {}, {}) }) {
            onNodeWithTag("account-avatar-empty").assertExists()
        }
    }

    @Test
    fun settings_shows_the_avatar_but_no_settings_row_of_its_own() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(
            gym,
            screen = { SettingsScreen(mode = ThemeMode.Dark, onModeChange = {}, onBack = {}) },
        ) {
            onNodeWithTag("account-avatar").assertExists()
            onNodeWithTag("account-avatar").performClick()
            onNodeWithTag("account-settings").assertDoesNotExist()
        }
    }

    @Test
    fun signing_out_keeps_what_the_account_recorded() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runBlocking { gym.sets.upsert(recordedSet(owner = ivan.account.userId)) }
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}) }) {
            onNodeWithTag("account-avatar").performClick()
            onNodeWithTag("account-sign-out").performClick()
            onNodeWithTag("account-avatar-empty").assertExists()
        }
        assertEquals(1, gym.sets.rows.size)
    }
}
