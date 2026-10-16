package monster.greyde.kachalochka.ui.plans

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PlanFormScreenTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)
    private val gone = Machine.new("Гакк", null, t0).copy(deleted = true)
    private val legs =
        Plan(PlanId.random(), null, "Ноги", listOf(press.id, gone.id), t0, t0, false)

    init {
        runBlocking {
            listOf(press, row, gone).forEach { gym.machines.upsert(it) }
            gym.plans.upsert(legs)
        }
    }

    private var done = 0
    private var addRequests = 0

    private fun stored() = gym.plans.rows.getValue(legs.id)

    @Test
    fun a_new_plan_writes_picked_machines_once_each_and_its_name_on_back() {
        var picked: MachineId? by mutableStateOf(null)
        runScreenTest(gym, screen = {
            PlanFormScreen(
                null,
                picked,
                { picked = null },
                {},
                onAddMachine = { addRequests++ },
                onDone = { done++ },
            )
        }) {
            waitForIdle()
            onNodeWithTag("save-plan").assertDoesNotExist()
            onNodeWithTag("plan-add-machine").performClick()
            picked = row.id
            waitForIdle()
            picked = press.id
            waitForIdle()
            picked = row.id
            waitForIdle()
            val written =
                gym.plans.rows.values
                    .single { it.id != legs.id }
            assertEquals(listOf(row.id, press.id), written.machineIds)
            assertEquals(null, written.userId)
            onNodeWithTag("plan-name").performTextReplacement("  Спина  ")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
        }
        assertEquals(1, addRequests)
        assertEquals(1, done)
        val saved =
            gym.plans.rows.values
                .single { it.id != legs.id }
        assertEquals("Спина", saved.name)
        assertEquals(listOf(row.id, press.id), saved.machineIds)
    }

    @Test
    fun a_saved_plan_shows_its_live_machines_and_writes_a_removal() =
        runScreenTest(gym, screen = {
            PlanFormScreen(legs.id, null, {}, {}, {}, onDone = { done++ })
        }) {
            waitForIdle()
            onNodeWithTag("plan-name").assertTextEquals("Ноги")
            onNodeWithTag("plan-machine-${press.id.value}").assertIsDisplayed()
            onNodeWithTag("plan-machine-${gone.id.value}").assertDoesNotExist()
            onNodeWithTag("remove-${press.id.value}").performClick()
            waitForIdle()
            assertEquals(emptyList(), stored().machineIds)
            assertEquals("Ноги", stored().name)
        }

    @Test
    fun the_name_is_written_when_its_field_loses_focus() =
        runScreenTest(gym, screen = {
            PlanFormScreen(legs.id, null, {}, {}, {}, onDone = { done++ })
        }) {
            waitForIdle()
            onNodeWithTag("plan-name").performTextReplacement("Спина")
            onNodeWithTag("plan-name").assertIsFocused()
            onNodeWithTag("plan-name").performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            onNodeWithTag("plan-name").assertIsNotFocused()
            assertEquals("Спина", stored().name)
            assertEquals(0, done)
        }

    @Test
    fun system_back_writes_the_name_and_closes_the_form() {
        val dispatcher = NavigationEventDispatcher()
        val systemBack = DirectNavigationEventInput().also(dispatcher::addInput)
        val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = dispatcher
            }
        runScreenTest(gym, screen = {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                PlanFormScreen(legs.id, null, {}, {}, {}, onDone = { done++ })
            }
        }) {
            waitForIdle()
            onNodeWithTag("plan-name").performTextReplacement("Спина")
            runOnIdle { systemBack.backCompleted() }
            waitForIdle()
        }
        assertEquals("Спина", stored().name)
        assertEquals(1, done)
    }

    @Test
    fun leaving_a_plan_with_neither_name_nor_machines_deletes_it() {
        runScreenTest(gym, screen = {
            PlanFormScreen(legs.id, null, {}, {}, {}, onDone = { done++ })
        }) {
            waitForIdle()
            onNodeWithTag("plan-name").performTextReplacement("")
            onNodeWithTag("remove-${press.id.value}").performClick()
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
        }
        assertTrue(stored().deleted)
        assertEquals(1, done)
    }

    @Test
    fun a_saved_plan_is_deleted_after_a_confirmation() {
        runScreenTest(gym, screen = {
            PlanFormScreen(legs.id, null, {}, {}, {}, onDone = { done++ })
        }) {
            waitForIdle()
            onNodeWithTag("plan-menu").performClick()
            onNodeWithTag("delete-plan").performClick()
            onNodeWithTag("cancel-delete-plan").performClick()
            assertEquals(false, stored().deleted)
            onNodeWithTag("plan-menu").performClick()
            onNodeWithTag("delete-plan").performClick()
            onNodeWithTag("confirm-delete-plan").performClick()
            waitForIdle()
        }
        assertTrue(stored().deleted)
        assertEquals(1, done)
    }

    @Test
    fun a_new_plan_has_no_menu() =
        runScreenTest(gym, screen = { PlanFormScreen(null, null, {}, {}, {}, {}) }) {
            waitForIdle()
            onNodeWithTag("plan-menu").assertDoesNotExist()
        }

    @Test
    fun switching_accounts_closes_the_form() {
        runScreenTest(gym, screen = {
            PlanFormScreen(legs.id, null, {}, {}, {}, onDone = { done++ })
        }) {
            waitForIdle()
            gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
            waitForIdle()
        }
        assertEquals(1, done)
        assertEquals(legs, stored())
    }
}
