package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FriendMachineScreenTest {
    private val on = signedInGym()
    private val olegPress =
        Machine.new("Жим ногами", OLEG.userId, on.clock.current).copy(setupNote = "Спинка на 4")

    init {
        on.friends.group("Зал на Лесной", owner = OLEG, ME)
        on.friends.machines += olegPress
    }

    @Test
    fun the_settings_are_read_only_and_taking_hands_on_the_copy() {
        val taken = mutableListOf<MachineId>()
        runScreenTest(on, screen = {
            FriendMachineScreen(olegPress.id, OLEG.userId, {}, {}, onTaken = { taken += it })
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Упражнение друга")
            onNodeWithTag("friend-machine-name").assertTextEquals("Жим ногами")
            onNodeWithTag("friend-machine-owner").assertTextEquals("Олег")
            onNodeWithTag("friend-machine-note").assertTextEquals("Спинка на 4")
            onAllNodes(hasSetTextAction()).assertCountEquals(0)

            onNodeWithTag("take-machine").performClick()
            waitForIdle()
        }
        val link =
            on.machineLinks.rows.values
                .single()
        assertEquals(taken.single() to olegPress.id, link.machineId to link.linkedMachineId)
    }

    @Test
    fun the_friend_s_photos_open_without_a_delete_button() {
        on.friends.photos += Photo.new(olegPress.id, OLEG.userId, on.clock.current)
        runScreenTest(on, screen = {
            FriendMachineScreen(olegPress.id, OLEG.userId, {}, {}, {})
        }) {
            onAllNodesWithTag("photo-thumbnail").assertCountEquals(1)
            onNodeWithTag("machine-photo").assertDoesNotExist()

            onNodeWithTag("photo-thumbnail").performClick()
            waitForIdle()

            onNodeWithTag("photo-viewer").assertExists()
            onNodeWithTag("delete-photo").assertDoesNotExist()
        }
    }

    @Test
    fun offline_it_offers_a_retry() {
        on.friends.offline = true
        runScreenTest(on, screen = {
            FriendMachineScreen(olegPress.id, OLEG.userId, {}, {}, {})
        }) {
            onNodeWithTag("friends-offline").assertTextEquals("Нет связи с сервером")
            onNodeWithTag("take-machine").assertDoesNotExist()
        }
    }
}
