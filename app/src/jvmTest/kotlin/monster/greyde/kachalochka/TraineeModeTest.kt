package monster.greyde.kachalochka

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.ui.account.AccountMenu
import monster.greyde.kachalochka.ui.calendar.CalendarScreen
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.traineeAccount
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import monster.greyde.kachalochka.ui.home.HomeScreen
import monster.greyde.kachalochka.ui.machine.MachineFormArgs
import monster.greyde.kachalochka.ui.machine.MachineFormScreen
import monster.greyde.kachalochka.ui.machine.MachineListScreen
import monster.greyde.kachalochka.ui.plans.PlansScreen
import monster.greyde.kachalochka.ui.visit.VisitScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Иван trains Саша and has switched to Саша, as a trainer does to plan for a trainee. */
@OptIn(ExperimentalTestApi::class)
class TraineeModeTest {
    private val gym = signedInGym().withChild(traineeAccount(SASHA, IVAN_SESSION))
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", SASHA.userId, t0)
    private val visit = Visit(VisitId.random(), SASHA.userId, gym.today, t0, t0, false)
    private val set =
        WorkoutSet(
            WorkoutSetId.random(),
            SASHA.userId,
            visit.id,
            press.id,
            70.0,
            10,
            0,
            t0,
            t0,
            false,
        )
    private val plan = Plan(PlanId.random(), SASHA.userId, "Ноги", listOf(press.id), t0, t0, false)

    init {
        runBlocking {
            gym.machines.upsert(press)
            gym.visits.upsert(visit)
            gym.sets.upsert(set)
            gym.plans.upsert(plan)
            gym.accounts.switchTo(SASHA.userId)
        }
    }

    @Test
    fun home_starts_no_visit() =
        runScreenTest(gym, screen = { HomeScreen({}, {}, {}, {}, {}, {}, {}) }) {
            waitForIdle()
            onNodeWithTag("today-date").assertExists()
            onNodeWithTag("open-today").assertDoesNotExist()
            onNodeWithTag("section-plans").assertExists()
            onNodeWithTag("section-measures").assertDoesNotExist()
        }

    @Test
    fun the_visit_is_only_looked_at() =
        runScreenTest(gym, screen = {
            VisitScreen(
                day = gym.today,
                pickedMachineId = null,
                onPickedMachineConsumed = {},
                onBack = {},
                onOpenSettings = {},
                onPickMachine = {},
                onOpenMachineSettings = {},
            )
        }) {
            waitForIdle()
            onNodeWithTag("pick-machine").assertDoesNotExist()
            onNodeWithTag("reorder-toggle").assertDoesNotExist()
            onNodeWithTag("group-${press.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("add-set").assertDoesNotExist()
            onNodeWithTag("set-row-${set.id.value}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("save-set").assertDoesNotExist()
        }

    @Test
    fun the_calendar_plans_from_a_visit_but_changes_none() =
        runScreenTest(gym, screen = { CalendarScreen({}, {}, {}, { _, _, _ -> }, {}) }) {
            waitForIdle()
            onNodeWithTag("visit-menu-${visit.id.value}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("save-as-plan-${visit.id.value}").assertExists()
            onNodeWithTag("move-visit-${visit.id.value}").assertDoesNotExist()
            onNodeWithTag("remove-visit-${visit.id.value}").assertDoesNotExist()
        }

    @Test
    fun a_plan_opens_but_starts_no_visit() {
        val opened = mutableListOf<PlanId?>()
        runScreenTest(gym, screen = { PlansScreen({}, {}, { opened += it }, {}) }) {
            waitForIdle()
            onNodeWithTag("start-plan-${plan.id.value}").assertDoesNotExist()
            onNodeWithTag("plan-row-${plan.id.value}").performClick()
        }
        assertEquals(listOf<PlanId?>(plan.id), opened)
    }

    @Test
    fun the_machines_show_no_friends_and_open_to_be_looked_at() {
        gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        gym.friends.machines += Machine.new("Жим Олега", OLEG.userId, t0)
        val opened = mutableListOf<Any>()
        var added = 0
        runScreenTest(gym, screen = {
            MachineListScreen({}, {}, { opened += it }, { added++ }, { _, _ -> })
        }) {
            waitForIdle()
            onNodeWithTag("machine-list-friend-section-${OLEG.userId.value}").assertDoesNotExist()
            onNodeWithTag("machine-list-row-${press.id.value}").performClick()
            onNodeWithTag("new-machine").performClick()
        }
        assertEquals(listOf<Any>(press.id), opened)
        assertEquals(1, added)
    }

    @Test
    fun a_trainee_s_machine_opens_read_only() =
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(press.id, null, ""), {}, {}, {})
        }) {
            waitForIdle()
            onNodeWithTag("machine-name").assertTextEquals("Жим ногами")
            onNodeWithTag("save-machine").assertDoesNotExist()
            onNodeWithTag("new-tag").assertDoesNotExist()
            onNodeWithTag("link-machine").assertDoesNotExist()
            onNodeWithTag("machine-photo").assertDoesNotExist()
        }

    @Test
    fun a_trainee_s_machine_without_sets_is_not_offered_for_deletion() {
        val unused = Machine.new("Гакк", SASHA.userId, t0)
        runBlocking { gym.machines.upsert(unused) }
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(unused.id, null, ""), {}, {}, {})
        }) {
            waitForIdle()
            onNodeWithTag("machine-menu").performClick()
            onNodeWithTag("delete-machine").assertDoesNotExist()
        }
    }

    @Test
    fun a_new_machine_is_added_for_the_trainee_without_photos() {
        var saved = 0
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, "Тяга"), {}, {}, { saved++ })
        }) {
            waitForIdle()
            onNodeWithTag("machine-photo").assertDoesNotExist()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()
        }
        assertEquals(1, saved)
        assertTrue(runBlocking { gym.machines.all(SASHA.userId) }.any { it.name == "Тяга" })
    }

    @Test
    fun the_account_menu_names_a_trainee_as_one() =
        runScreenTest(gym, screen = {
            AccountMenu(onOpenSettings = null, Modifier.testTag("account-avatar"))
        }) {
            waitForIdle()
            onNodeWithTag("account-avatar").performClick()
            waitForIdle()
            onNodeWithText("Ученик").assertExists()
        }
}
