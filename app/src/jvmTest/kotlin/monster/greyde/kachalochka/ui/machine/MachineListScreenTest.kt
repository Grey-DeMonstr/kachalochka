package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
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
    fun a_sort_chip_orders_the_list_and_is_remembered() {
        runBlocking { gym.machines.upsert(press) }
        runScreenTest(gym, screen = { MachineListScreen({}, {}, {}, {}, { _, _ -> }) }) {
            onNodeWithTag("sort-recent").assertTextEquals("Недавние")
            onNodeWithTag("sort-frequent").assertTextEquals("Частые")
            onNodeWithTag("sort-name").assertTextEquals("А–Я").performClick()
            waitForIdle()
        }
        assertEquals(MachineSort.Name, runBlocking { gym.profiles.forOwner(null)?.machineSort })
    }

    @Test
    fun a_search_and_a_tag_narrow_the_list() {
        val row = Machine.new("Тяга", null, gym.clock.current).copy(tags = setOf("Спина"))
        runBlocking {
            gym.machines.upsert(press)
            gym.machines.upsert(row)
        }
        runScreenTest(gym, screen = { MachineListScreen({}, {}, {}, {}, { _, _ -> }) }) {
            onNodeWithTag("machine-search").performTextInput("гакк")
            waitForIdle()
            onNodeWithTag("machine-list-nothing").assertTextEquals("Ничего не найдено")
            onNodeWithTag("clear-search").performClick()
            onNodeWithTag("list-tag-Спина").performClick()
            waitForIdle()

            onNodeWithTag("machine-list-row-${row.id.value}").assertIsDisplayed()
            onNodeWithTag("machine-list-row-${press.id.value}").assertDoesNotExist()
        }
    }

    @Test
    fun a_machine_names_the_friends_machines_it_is_linked_with() {
        val on = signedInGym()
        on.friends.group("Зал на Лесной", owner = OLEG, ME)
        val olegs = Machine.new("Гакк-машина", OLEG.userId, on.clock.current)
        val (mine, link) = linkedCopy(olegs, ME.userId, on.clock.current)
        on.friends.machines += olegs
        runBlocking {
            on.machines.upsert(mine)
            on.machineLinks.upsert(link)
        }
        runScreenTest(on, screen = { MachineListScreen({}, {}, {}, {}, { _, _ -> }) }) {
            waitForIdle()
            val row = hasAnyAncestor(hasTestTag("card-linked-${mine.id.value}-${olegs.id.value}"))
            onNode(hasText("Гакк-машина") and row, useUnmergedTree = true).assertIsDisplayed()
            onNode(hasText("О") and row, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun without_machines_the_list_says_so() =
        runScreenTest(gym, screen = { MachineListScreen({}, {}, {}, {}, { _, _ -> }) }) {
            onNodeWithTag("machine-list-empty").assertTextEquals("Упражнений пока нет")
            onNodeWithTag("machine-list-friend-section-${OLEG.userId.value}").assertDoesNotExist()
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
            onNodeWithTag("machine-list-friend-section-${OLEG.userId.value}")
                .performScrollTo()
                .assertIsDisplayed()
            onNodeWithTag("machine-list-friend-${olegPress.id.value}")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            waitForIdle()

            assertEquals(listOf(olegPress.id to OLEG.userId), opened)
        }
    }
}
