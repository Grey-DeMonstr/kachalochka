package monster.greyde.kachalochka.ui.plans

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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

    @Test
    fun a_new_plan_takes_picked_machines_once_each_and_saves() {
        var picked: MachineId? by mutableStateOf(null)
        runScreenTest(gym, screen = {
            PlanFormScreen(
                null,
                picked,
                { picked = null },
                {},
                {},
                onAddMachine = { addRequests++ },
                onDone = { done++ },
            )
        }) {
            waitForIdle()
            onNodeWithTag("save-plan").assertIsNotEnabled()
            onNodeWithTag("plan-add-machine").performClick()
            picked = row.id
            waitForIdle()
            picked = press.id
            waitForIdle()
            picked = row.id
            waitForIdle()
            onNodeWithTag("plan-name").performTextReplacement("  Спина  ")
            onNodeWithTag("save-plan").performClick()
            waitForIdle()
        }
        assertEquals(1, addRequests)
        assertEquals(1, done)
        val saved =
            gym.plans.rows.values
                .single { it.id != legs.id }
        assertEquals("Спина", saved.name)
        assertEquals(listOf(row.id, press.id), saved.machineIds)
        assertEquals(null, saved.userId)
    }

    @Test
    fun a_saved_plan_shows_its_live_machines_and_drops_a_removed_one() =
        runScreenTest(gym, screen = {
            PlanFormScreen(legs.id, null, {}, {}, {}, {}, onDone = { done++ })
        }) {
            waitForIdle()
            onNodeWithTag("plan-name").assertTextEquals("Ноги")
            onNodeWithTag("plan-machine-${press.id.value}").assertIsDisplayed()
            onNodeWithTag("plan-machine-${gone.id.value}").assertDoesNotExist()
            onNodeWithTag("remove-${press.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("save-plan").assertIsNotEnabled()
        }

    @Test
    fun leaving_without_saving_writes_nothing() {
        runScreenTest(gym, screen = {
            PlanFormScreen(legs.id, null, {}, {}, {}, {}, onDone = { done++ })
        }) {
            waitForIdle()
            onNodeWithTag("plan-name").performTextReplacement("Спина")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
        }
        assertEquals(
            "Ноги",
            gym.plans.rows
                .getValue(legs.id)
                .name,
        )
    }

    @Test
    fun a_saved_plan_is_deleted_after_a_confirmation() {
        runScreenTest(gym, screen = {
            PlanFormScreen(legs.id, null, {}, {}, {}, {}, onDone = { done++ })
        }) {
            waitForIdle()
            onNodeWithTag("plan-menu").performClick()
            onNodeWithTag("delete-plan").performClick()
            onNodeWithTag("cancel-delete-plan").performClick()
            assertEquals(
                false,
                gym.plans.rows
                    .getValue(legs.id)
                    .deleted,
            )
            onNodeWithTag("plan-menu").performClick()
            onNodeWithTag("delete-plan").performClick()
            onNodeWithTag("confirm-delete-plan").performClick()
            waitForIdle()
        }
        assertTrue(
            gym.plans.rows
                .getValue(legs.id)
                .deleted,
        )
        assertEquals(1, done)
    }

    @Test
    fun a_new_plan_has_no_menu() =
        runScreenTest(gym, screen = { PlanFormScreen(null, null, {}, {}, {}, {}, {}) }) {
            waitForIdle()
            onNodeWithTag("plan-menu").assertDoesNotExist()
        }

    @Test
    fun switching_accounts_closes_the_form_without_writing() {
        runScreenTest(gym, screen = {
            PlanFormScreen(legs.id, null, {}, {}, {}, {}, onDone = { done++ })
        }) {
            waitForIdle()
            gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
            waitForIdle()
        }
        assertEquals(1, done)
        assertEquals(legs, gym.plans.rows.getValue(legs.id))
    }
}
