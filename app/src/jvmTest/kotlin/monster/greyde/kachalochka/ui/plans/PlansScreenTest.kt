package monster.greyde.kachalochka.ui.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalTestApi::class)
class PlansScreenTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)
    private val gone = Machine.new("Гакк", null, t0).copy(deleted = true)
    private val legs =
        Plan(PlanId.random(), null, "Ноги", listOf(press.id, gone.id), t0, t0, false)
    private val unnamed =
        Plan(PlanId.random(), null, " ", listOf(row.id, press.id), t0 + 1.hours, t0, false)

    init {
        runBlocking {
            listOf(press, row, gone).forEach { gym.machines.upsert(it) }
            gym.plans.upsert(legs)
            gym.plans.upsert(unnamed)
        }
    }

    private fun plans(
        opened: MutableList<PlanId?> = mutableListOf(),
        started: MutableList<CalendarDay> = mutableListOf(),
    ): @Composable () -> Unit =
        {
            PlansScreen({}, {}, onOpenPlan = { opened += it }, onStarted = { started += it })
        }

    @Test
    fun plans_list_oldest_first_by_name_or_machines_with_their_live_count() =
        runScreenTest(gym, screen = plans()) {
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Планы")
            onNodeWithTag(
                "plan-title-${legs.id.value}",
                useUnmergedTree = true,
            ).assertTextEquals("Ноги")
            onNodeWithTag(
                "plan-count-${legs.id.value}",
                useUnmergedTree = true,
            ).assertTextEquals("1 упражнение")
            onNodeWithTag(
                "plan-title-${unnamed.id.value}",
                useUnmergedTree = true,
            ).assertTextEquals("Тяга, Жим ногами")
            onNodeWithTag("no-plans").assertDoesNotExist()
        }

    @Test
    fun without_plans_the_list_says_so_and_offers_a_new_one() {
        runBlocking { gym.plans.rows.clear() }
        val opened = mutableListOf<PlanId?>()
        runScreenTest(gym, screen = plans(opened)) {
            waitForIdle()
            onNodeWithTag("no-plans").assertIsDisplayed()
            onNodeWithTag("new-plan").performClick()
            onNodeWithTag("new-plan").assertIsDisplayed()
        }
        assertEquals(listOf<PlanId?>(null), opened)
    }

    @Test
    fun a_row_opens_its_plan() {
        val opened = mutableListOf<PlanId?>()
        runScreenTest(gym, screen = plans(opened)) {
            waitForIdle()
            onNodeWithTag("plan-row-${legs.id.value}").performClick()
        }
        assertEquals(listOf<PlanId?>(legs.id), opened)
    }

    @Test
    fun starting_a_plan_adds_its_machines_to_today_s_visit_and_deletes_it() {
        val started = mutableListOf<CalendarDay>()
        runScreenTest(gym, screen = plans(started = started)) {
            waitForIdle()
            onNodeWithTag("start-plan-${unnamed.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("confirm-start-plan").performClick()
            waitForIdle()
        }
        assertEquals(listOf(gym.today), started)
        val visit =
            gym.visits.rows.values
                .single()
        assertEquals(listOf(row.id, press.id), visit.planned)
        assertTrue(
            gym.plans.rows
                .getValue(unnamed.id)
                .deleted,
        )
    }

    @Test
    fun a_cancelled_start_keeps_the_plan_and_writes_no_visit() {
        val started = mutableListOf<CalendarDay>()
        runScreenTest(gym, screen = plans(started = started)) {
            waitForIdle()
            onNodeWithTag("start-plan-${legs.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("cancel-start-plan").performClick()
            waitForIdle()
            onNodeWithTag("confirm-start-plan").assertDoesNotExist()
            onNodeWithTag("plan-row-${legs.id.value}").assertIsDisplayed()
        }
        assertTrue(started.isEmpty())
        assertTrue(gym.visits.rows.isEmpty())
        assertFalse(
            gym.plans.rows
                .getValue(legs.id)
                .deleted,
        )
    }

    @Test
    fun a_switch_lists_the_new_account_s_plans() {
        runScreenTest(gym, screen = plans()) {
            waitForIdle()
            gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
            waitForIdle()
            onNodeWithTag("plan-row-${legs.id.value}").assertDoesNotExist()
            onNodeWithTag("no-plans").assertIsDisplayed()
        }
    }
}
