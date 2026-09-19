package monster.greyde.kachalochka.ui.visit

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class VisitScreenTest {
    private val gym = FakeGym()
    private val visit =
        Visit(VisitId.random(), null, gym.clock.current, null, gym.clock.current, false)
    private val press = Machine.new("Жим ногами", null, gym.clock.current)

    init {
        runBlocking {
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
        }
        gym.clock.current += 42.minutes + 10.seconds
    }

    @Test
    fun a_fresh_visit_shows_its_time_and_asks_for_a_machine() {
        var picks = 0
        runScreenTest(gym, screen = { visitScreen(onPickMachine = { picks++ }) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Визит · 42:10")
            onNodeWithTag("visit-set-count").assertTextEquals("0 ПОДХОДОВ")
            onNodeWithTag("pick-machine").performClick()
            waitForIdle()
            assertEquals(1, picks)
        }
    }

    @Test
    fun a_picked_machine_fills_the_sheet_and_a_saved_set_joins_the_list() {
        var consumed = 0
        runScreenTest(
            gym,
            screen = { visitScreen(picked = press.id, onConsumed = { consumed++ }) },
        ) {
            waitForIdle()
            onNodeWithTag("sheet-machine-name").assertTextEquals("Жим ногами")
            onNodeWithTag("weight-value").assertTextEquals("0")
            onNodeWithTag("set-comment").assertIsNotEnabled()
            onNodeWithTag("weight-plus").performClick()
            onNodeWithTag("save-set").performClick()
            waitForIdle()

            onNodeWithTag("visit-set-count").assertTextEquals("1 ПОДХОД")
            onNodeWithTag("sheet-set-number").assertTextEquals("подход 2")
            onNodeWithTag("rest-timer").assertTextEquals("1:30")
            assertEquals(1, consumed)
        }
    }

    @Test
    fun ending_the_visit_reports_back() {
        var ended = 0
        runScreenTest(gym, screen = { visitScreen(onEnded = { ended++ }) }) {
            onNodeWithTag("end-visit").performClick()
            waitForIdle()
            assertEquals(1, ended)
        }
    }

    @Composable
    private fun visitScreen(
        picked: MachineId? = null,
        onConsumed: () -> Unit = {},
        onPickMachine: (MachineId?) -> Unit = {},
        onEnded: () -> Unit = {},
    ) = VisitScreen(
        visitId = visit.id,
        pickedMachineId = picked,
        onPickedMachineConsumed = onConsumed,
        onBack = {},
        onOpenSettings = {},
        onPickMachine = onPickMachine,
        onOpenMachineSettings = {},
        onVisitEnded = onEnded,
    )
}
