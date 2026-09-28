package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MachinePickerScreenTest {
    private val gym = FakeGym()
    private val visit =
        Visit(VisitId.random(), null, gym.today, gym.clock.current, gym.clock.current, false)
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
                gym.today,
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
            screen = { MachinePickerScreen(gym.today, null, {}, {}, {}, {}, { _, _ -> }) },
        ) {
            onNodeWithTag("copy-machine").assertDoesNotExist()
        }

    @Test
    fun a_friend_s_machine_is_picked_as_a_linked_copy() {
        val on = signedInGym()
        on.friends.group("Зал на Лесной", owner = OLEG, ME)
        val olegPress = Machine.new("Жим ногами", OLEG.userId, on.clock.current)
        on.friends.machines += olegPress
        val picked = mutableListOf<MachineId>()
        runScreenTest(on, screen = {
            MachinePickerScreen(on.today, null, {}, {}, { picked += it }, {}, { _, _ -> })
        }) {
            onNodeWithTag("picker-friends").performScrollTo().assertIsDisplayed()
            onNodeWithTag("friend-machine-${olegPress.id.value}").performScrollTo().performClick()
            waitForIdle()
        }
        val link =
            on.machineLinks.rows.values
                .single()
        assertEquals(picked.single() to olegPress.id, link.machineId to link.linkedMachineId)
    }
}
