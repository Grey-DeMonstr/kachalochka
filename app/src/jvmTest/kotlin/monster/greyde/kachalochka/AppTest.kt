package monster.greyde.kachalochka

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalTestApi::class)
class AppTest {
    private val gym = FakeGym()

    private fun runApp(assertions: ComposeUiTest.() -> Unit) =
        runNavigationUiTest(content = { TestKoin(gym) { App() } }, assertions = assertions)

    @Test
    fun the_app_opens_on_home_with_the_sections_disabled() =
        runApp {
            onNodeWithTag("top-bar-title").assertTextEquals("Качалочка")
            onNodeWithTag("top-bar-back").assertDoesNotExist()
            onNodeWithTag("section-plans").assertIsNotEnabled()
            onNodeWithTag("section-stats").assertIsNotEnabled()
            onNodeWithTag("section-friends").assertIsNotEnabled()
            onNodeWithTag("section-machines").assertIsEnabled()
        }

    @Test
    fun home_navigates_to_settings_and_back() =
        runApp {
            onNodeWithTag("account-avatar").performClick()
            onNodeWithTag("account-settings").performClick()
            waitForIdle()
            onNodeWithTag("settings-title").assertIsDisplayed()
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("open-today").assertIsDisplayed()
        }

    @Test
    fun home_opens_today_and_comes_back_to_it() =
        runApp {
            onNodeWithTag("open-today").performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Сегодня")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("visit-counts").assertTextEquals("Подходов пока нет")
        }

    @Test
    fun a_day_added_on_the_calendar_opens_on_the_visit_screen() =
        runApp {
            onNodeWithTag("section-visits").performClick()
            waitForIdle()
            onNodeWithTag("day-2023-11-10").performClick()
            waitForIdle()
            onNodeWithTag("add-visit").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визит · 10 ноября")

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithTag("top-bar-title").assertTextEquals("Визиты")
        }

    @Test
    fun picking_a_machine_from_the_visit_fills_the_sheet() {
        val press = Machine.new("Жим ногами", null, gym.clock.current)
        runBlocking { gym.machines.upsert(press) }
        runApp {
            onNodeWithTag("open-today").performClick()
            waitForIdle()
            onNodeWithTag("pick-machine").performClick()
            waitForIdle()
            onNodeWithTag("machine-row-${press.id.value}").performClick()
            waitForIdle()
            onNodeWithTag(
                "sheet-machine-name",
                useUnmergedTree = true,
            ).assertTextEquals("Жим ногами")
        }
    }

    @Test
    fun a_machine_created_from_the_picker_lands_in_the_visit_sheet() =
        runApp {
            onNodeWithTag("open-today").performClick()
            waitForIdle()
            onNodeWithTag("pick-machine").performClick()
            waitForIdle()
            onNodeWithTag("machine-search").performTextInput("Гакк")
            waitForIdle()
            onNodeWithTag("create-machine").performClick()
            waitForIdle()
            onNodeWithTag("machine-name").assertTextContains("Гакк")
            onNodeWithTag("save-machine").performClick()
            waitForIdle()

            onNodeWithTag(
                "sheet-machine-name",
                useUnmergedTree = true,
            ).assertTextEquals("Гакк")
        }

    @Test
    fun home_opens_the_visit_calendar_and_a_past_visit_from_it() {
        val now = gym.clock.current
        val past =
            Visit(VisitId.random(), null, CalendarDay(2023, 11, 12), now - 2.days, now, false)
        runBlocking { gym.visits.upsert(past) }
        runApp {
            onNodeWithTag("section-visits").performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визиты")
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("calendar-visit-${past.id.value}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визит · 12 ноября")

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визиты")
        }
    }

    @Test
    fun the_calendar_shows_a_visit_recorded_while_another_was_open() {
        val now = gym.clock.current
        val twelfth = CalendarDay(2023, 11, 12)
        val past = Visit(VisitId.random(), null, twelfth, now - 2.days, now, false)
        val later = Visit(VisitId.random(), null, twelfth, now - 2.days + 1.hours, now, false)
        runBlocking { gym.visits.upsert(past) }
        runApp {
            onNodeWithTag("section-visits").performClick()
            waitForIdle()
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("calendar-visit-${past.id.value}").performScrollTo().performClick()
            waitForIdle()
            runBlocking { gym.visits.upsert(later) }

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithTag("calendar-visit-${later.id.value}").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun a_machine_added_from_the_machine_list_shows_up_in_it() =
        runApp {
            onNodeWithTag("section-machines").performClick()
            waitForIdle()
            onNodeWithTag("new-machine").performClick()
            waitForIdle()
            onNodeWithTag("machine-name").performTextInput("Гакк")
            onNodeWithTag("save-machine").performClick()
            waitForIdle()

            onNodeWithTag("top-bar-title").assertTextEquals("Тренажёры")
            val gakk = runBlocking { gym.machines.all(null) }.single()
            onNodeWithTag("machine-list-row-${gakk.id.value}").assertIsDisplayed()
        }

    @Test
    fun a_machine_edited_from_the_list_returns_to_it() {
        val press = Machine.new("Жим ногами", null, gym.clock.current)
        runBlocking { gym.machines.upsert(press) }
        runApp {
            onNodeWithTag("section-machines").performClick()
            waitForIdle()
            onNodeWithTag("machine-list-row-${press.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("machine-name").assertTextContains("Жим ногами")
            onNodeWithTag("step-10").performScrollTo().performClick()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()

            onNodeWithTag("top-bar-title").assertTextEquals("Тренажёры")
            assertEquals(10.0, runBlocking { gym.machines.byId(press.id) }?.weightStep)
        }
    }

    @Test
    fun a_signed_in_account_creates_a_group_from_home() {
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
        runApp {
            onNodeWithTag("section-friends").performClick()
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Друзья")
            onNodeWithTag("create-group").performClick()
            waitForIdle()
            onNodeWithTag("group-dialog-field").performTextInput("Зал на Лесной")
            onNodeWithTag("group-dialog-confirm").performClick()
            waitForIdle()

            onNodeWithTag("top-bar-title").assertTextEquals("Зал на Лесной")
        }
    }

    @Test
    fun leaving_a_group_updates_the_list_on_return() {
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        runApp {
            onNodeWithTag("section-friends").performClick()
            waitForIdle()
            onNodeWithTag("group-row-${group.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("leave-group").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("group-confirm").performClick()
            waitForIdle()

            onNodeWithTag("top-bar-title").assertTextEquals("Друзья")
            onNodeWithTag("groups-empty").assertTextEquals("Групп пока нет")
        }
    }

    @Test
    fun a_stored_invite_opens_its_group_once_an_account_is_active() {
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
        gym.friends.group("Зал на Лесной", owner = OLEG, code = "ABCD2345")
        gym.joinCodes.save("ABCD2345")
        runApp {
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Зал на Лесной")
        }
        assertNull(gym.joinCodes.code())
    }

    @Test
    fun a_stored_invite_nobody_has_says_so() {
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
        gym.joinCodes.save("ZZZZ2345")
        runApp {
            waitForIdle()
            onNodeWithTag("invite-missing").assertIsDisplayed()
            onNodeWithTag("invite-missing-ok").performClick()
            waitForIdle()
            onNodeWithTag("invite-missing").assertDoesNotExist()
        }
    }
}
