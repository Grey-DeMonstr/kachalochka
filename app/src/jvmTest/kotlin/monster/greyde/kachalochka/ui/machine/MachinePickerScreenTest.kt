package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MachinePickerScreenTest {
    private val gym = FakeGym()
    private val visit =
        Visit(VisitId.random(), null, gym.clock.current, null, gym.clock.current, false)
    private val press = Machine.new("Жим ногами", null, gym.clock.current)

    init {
        runBlocking {
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
        }
    }

    @Test
    fun choosing_creating_and_copying_report_back() {
        val picked = mutableListOf<MachineId>()
        val created = mutableListOf<String>()
        val copied = mutableListOf<Pair<MachineId, String>>()
        runScreenTest(gym, screen = {
            MachinePickerScreen(
                visit.id,
                selectedMachineId = press.id,
                onBack = {},
                onOpenSettings = {},
                onPicked = { picked += it },
                onCreate = { created += it },
                onCopy = { s, n -> copied += s to n },
            )
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Тренажёр")
            onNodeWithTag("machine-row-${press.id.value}").performClick()
            onNodeWithTag("machine-search").performTextInput("гакк")
            waitForIdle()
            onNodeWithTag("create-machine").performClick()
            onNodeWithTag("copy-machine").performClick()
            waitForIdle()

            assertEquals(listOf(press.id), picked)
            assertEquals(listOf("гакк"), created)
            assertEquals(listOf(press.id to "гакк"), copied)
        }
    }

    @Test
    fun without_a_chosen_machine_there_is_nothing_to_copy() =
        runScreenTest(
            gym,
            screen = { MachinePickerScreen(visit.id, null, {}, {}, {}, {}, { _, _ -> }) },
        ) {
            onNodeWithTag("copy-machine").assertDoesNotExist()
        }
}
