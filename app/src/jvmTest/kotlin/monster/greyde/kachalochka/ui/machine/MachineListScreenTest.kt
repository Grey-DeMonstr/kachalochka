package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInEnglish
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
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
            MachineListScreen(
                {},
                {},
                onOpenMachine = { opened += it },
                onNewMachine = { added++ },
                onOpenFriendMachine = { _, _ -> },
            )
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Упражнения")
            onNodeWithTag("machine-list-row-${press.id.value}").performClick()
            onNodeWithTag("new-machine").performClick()
            waitForIdle()

            assertEquals(listOf(press.id), opened)
            assertEquals(1, added)
        }
    }

    @Test
    fun without_machines_the_list_says_so() =
        runScreenTest(gym, screen = { MachineListScreen({}, {}, {}, {}, { _, _ -> }) }) {
            onNodeWithTag("machine-list-empty").assertTextEquals("Упражнений пока нет")
            onNodeWithTag("machine-list-friends").assertDoesNotExist()
        }

    @Test
    fun the_machine_list_speaks_english() =
        runScreenTestInEnglish(gym, screen = { MachineListScreen({}, {}, {}, {}, { _, _ -> }) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Machines")
            onNodeWithTag("machine-list-empty").assertTextEquals("No machines yet")
        }

    @Test
    fun a_friend_s_machine_is_listed_under_its_own_title_and_opens_with_its_owner() {
        val on = signedInGym()
        on.friends.group("Зал на Лесной", owner = OLEG, ME)
        val olegPress = Machine.new("Жим ногами", OLEG.userId, on.clock.current)
        on.friends.machines += olegPress
        val opened = mutableListOf<Pair<MachineId, UserId>>()
        runScreenTest(on, screen = {
            MachineListScreen({}, {}, {}, {}, onOpenFriendMachine = { id, owner ->
                opened += id to owner
            })
        }) {
            onNodeWithTag("machine-list-friends")
                .performScrollTo()
                .assertTextEquals("УПРАЖНЕНИЯ ДРУЗЕЙ")
            onNodeWithTag("machine-list-friend-${olegPress.id.value}")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            waitForIdle()

            assertEquals(listOf(olegPress.id to OLEG.userId), opened)
        }
    }
}
