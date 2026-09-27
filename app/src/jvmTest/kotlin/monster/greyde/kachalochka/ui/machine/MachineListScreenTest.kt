package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MachineListScreenTest {
    private val gym = FakeGym()
    private val press = Machine.new("Жим ногами", null, gym.clock.current)

    @Test
    fun a_machine_opens_for_editing_and_the_button_adds_one() {
        runBlocking { gym.machines.upsert(press) }
        val opened = mutableListOf<MachineId>()
        var added = 0
        runScreenTest(gym, screen = {
            MachineListScreen({}, {}, onOpenMachine = { opened += it }, onNewMachine = { added++ })
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Тренажёры")
            onNodeWithTag("machine-list-row-${press.id.value}").performClick()
            onNodeWithTag("new-machine").performClick()
            waitForIdle()

            assertEquals(listOf(press.id), opened)
            assertEquals(1, added)
        }
    }

    @Test
    fun without_machines_the_list_says_so() =
        runScreenTest(gym, screen = { MachineListScreen({}, {}, {}, {}) }) {
            onNodeWithTag("machine-list-empty").assertTextEquals("Тренажёров пока нет")
        }
}
