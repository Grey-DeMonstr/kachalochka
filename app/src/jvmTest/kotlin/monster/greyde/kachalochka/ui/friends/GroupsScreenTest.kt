package monster.greyde.kachalochka.ui.friends

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CompletableDeferred
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInEnglish
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupsScreenTest {
    private val gym = signedInGym()

    @Test
    fun a_group_row_opens_the_group() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        val opened = mutableListOf<GroupId>()
        runScreenTest(gym, screen = { GroupsScreen({}, {}, onOpenGroup = { opened += it }) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Друзья")
            onNodeWithTag("group-row-${group.id.value}").performClick()
            waitForIdle()
        }
        assertEquals(listOf(group.id), opened)
    }

    @Test
    fun groups_show_as_loading_until_the_read_completes() {
        val gate = CompletableDeferred<Unit>()
        gym.friends.gate = gate
        val group = gym.friends.group("Зал на Лесной", owner = ME)
        runScreenTest(gym, screen = { GroupsScreen({}, {}, {}) }) {
            onNodeWithTag("groups-loading").assertExists()
            onAllNodesWithTag("groups-empty").assertCountEquals(0)
            gate.complete(Unit)
            waitForIdle()
            onNodeWithTag("group-row-${group.id.value}").assertExists()
            onAllNodesWithTag("groups-loading").assertCountEquals(0)
        }
    }

    @Test
    fun without_groups_the_list_says_so() =
        runScreenTest(gym, screen = { GroupsScreen({}, {}, {}) }) {
            onNodeWithTag("groups-empty").assertTextEquals("Групп пока нет")
        }

    @Test
    fun the_groups_screen_speaks_english() =
        runScreenTestInEnglish(gym, screen = { GroupsScreen({}, {}, {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Friends")
            onNodeWithTag("groups-empty").assertTextEquals("No groups yet")
        }

    @Test
    fun a_group_created_in_the_dialog_opens() {
        val opened = mutableListOf<GroupId>()
        runScreenTest(gym, screen = { GroupsScreen({}, {}, onOpenGroup = { opened += it }) }) {
            onNodeWithTag("create-group").performClick()
            waitForIdle()
            onNodeWithTag("group-dialog-field").performTextInput("Зал")
            waitForIdle()
            onNodeWithTag("group-dialog-confirm").performClick()
            waitForIdle()
        }
        assertEquals(
            gym.friends.groups.keys
                .toList(),
            opened,
        )
    }

    @Test
    fun an_unknown_code_is_reported_in_the_dialog() =
        runScreenTest(gym, screen = { GroupsScreen({}, {}, {}) }) {
            onNodeWithTag("join-by-code").performClick()
            waitForIdle()
            onNodeWithTag("group-dialog-field").performTextInput("zzzz2345")
            waitForIdle()
            onNodeWithTag("group-dialog-field").assertTextEquals("ZZZZ2345")
            onNodeWithTag("group-dialog-confirm").performClick()
            waitForIdle()
            onNodeWithTag("group-dialog-error").assertTextEquals("Приглашение не найдено")
        }

    @Test
    fun mounting_the_screen_reads_the_groups_once() {
        gym.friends.group("Зал на Лесной", owner = ME)
        runScreenTest(gym, screen = { GroupsScreen({}, {}, {}) }) {
            waitForIdle()
            assertEquals(1, gym.friends.reads)
        }
    }

    @Test
    fun offline_it_offers_a_retry() {
        gym.friends.offline = true
        runScreenTest(gym, screen = { GroupsScreen({}, {}, {}) }) {
            onNodeWithTag("friends-offline").assertTextEquals("Нет связи с сервером")
            gym.friends.offline = false
            onNodeWithTag("friends-retry").performClick()
            waitForIdle()
            onNodeWithTag("friends-offline").assertDoesNotExist()
        }
    }
}
