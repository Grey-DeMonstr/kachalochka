package monster.greyde.kachalochka.ui.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
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
    "access-$name",
    "refresh-$name",
    Instant.fromEpochSeconds(1_700_000_000),
)

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Ivan")

    @Test
    fun friends_stay_locked_until_an_account_is_signed_in() =
        runScreenTest(FakeGym(), screen = { HomeScreen({}, {}, {}, {}) }) {
            onNodeWithTag("section-friends-lock", useUnmergedTree = true).assertExists()
        }

    @Test
    fun friends_unlock_once_an_account_is_signed_in() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}, {}) }) {
            onNodeWithTag("section-friends-lock", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun the_sign_in_button_stays_hidden_without_supabase_credentials() {
        val gym = FakeGym(credentials = SupabaseCredentials("", ""))
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}, {}) }) {
            onNodeWithTag("home-sign-in").assertDoesNotExist()
        }
    }

    /** The fake sign-in has nothing queued to hand back, which is a refusal like any other. */
    @Test
    fun a_refused_sign_in_says_so_under_the_button() =
        runScreenTest(FakeGym(), screen = { HomeScreen({}, {}, {}, {}) }) {
            onNodeWithTag("sign-in-failure").assertDoesNotExist()

            onNodeWithTag("home-sign-in").performClick()
            waitForIdle()

            onNodeWithTag("sign-in-failure").assertExists()
        }

    /** Credential Manager throws without the client id, so the button must not be offered. */
    @Test
    fun the_sign_in_button_stays_hidden_without_a_google_web_client_id() {
        val gym = FakeGym(credentials = SupabaseCredentials("https://example.test", "anon-key"))
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}, {}) }) {
            onNodeWithTag("home-sign-in").assertDoesNotExist()
        }
    }

    @Test
    fun the_today_card_offers_the_first_set_and_opens_today() {
        val opened = mutableListOf<CalendarDay>()
        runScreenTest(FakeGym(), screen = { HomeScreen({ opened += it }, {}, {}, {}) }) {
            onNodeWithTag("visit-counts").assertTextEquals("Подходов пока нет")
            onNodeWithTag("open-today").assertTextEquals("Записать подход")
            onNodeWithTag("open-today").performClick()
            waitForIdle()
        }
        assertEquals(listOf(CalendarDay(2023, 11, 14)), opened)
    }

    @Test
    fun the_today_card_offers_to_continue_once_today_has_a_set() {
        val gym = FakeGym()
        val t0 = gym.clock.current
        val visit = Visit(VisitId.random(), null, gym.today, t0, t0, false)
        val press = Machine.new("Жим ногами", null, t0)
        runBlocking {
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    visit.id,
                    press.id,
                    70.0,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                ),
            )
        }
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}, {}) }) {
            onNodeWithTag("visit-counts").assertTextEquals("1 тренажёр · 1 подход")
            onNodeWithTag("open-today").assertTextEquals("Продолжить")
        }
    }

    @Test
    fun the_machines_row_opens_the_machine_list() {
        var opened = 0
        runScreenTest(
            FakeGym(),
            screen = { HomeScreen({}, {}, {}, onOpenMachines = { opened++ }) },
        ) {
            onNodeWithTag("section-machines").performClick()
            waitForIdle()
        }
        assertEquals(1, opened)
    }
}
