package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

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
    fun link_to_mine_hands_on_the_machine_s_name_and_a_linked_machine_opens() {
        val pashas = Machine.new("Платформа", PASHA.userId, on.clock.current)
        on.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        on.friends.machines += pashas
        on.friends.links +=
            MachineLink(
                MachineLinkId.random(),
                PASHA.userId,
                pashas.id,
                olegPress.id,
                on.clock.current,
                false,
            )
        val linking = mutableListOf<String>()
        val opened = mutableListOf<Pair<MachineId, UserId>>()
        runScreenTest(on, screen = {
            FriendMachineScreen(
                olegPress.id,
                OLEG.userId,
                {},
                {},
                onTaken = {},
                onLinkToMine = { linking += it },
                onOpenFriendMachine = { machine, owner -> opened += machine to owner },
            )
        }) {
            onNodeWithTag("link-to-mine").performClick()
            onNodeWithTag("friend-machine-linked-${pashas.id.value}").performClick()
            waitForIdle()
        }
        assertEquals(listOf("Жим ногами"), linking)
        assertEquals(listOf(pashas.id to PASHA.userId), opened)
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
    fun the_tags_the_starred_cover_and_the_statistics_are_the_friend_s() {
        val first = Photo.new(olegPress.id, OLEG.userId, on.clock.current)
        val second = Photo.new(olegPress.id, OLEG.userId, on.clock.current + 1.minutes)
        on.friends.photos += listOf(first, second)
        on.friends.machines.clear()
        on.friends.machines += olegPress.copy(tags = setOf("Ноги"), coverPhoto = second.id)
        val visit = VisitId.random()
        on.friends.sets +=
            listOf(80.0 to 8, 85.0 to 6).mapIndexed { i, (weight, reps) ->
                WorkoutSet(
                    WorkoutSetId.random(),
                    OLEG.userId,
                    visit,
                    olegPress.id,
                    weight,
                    reps,
                    i + 1,
                    on.clock.current - 1.days + i.minutes,
                    on.clock.current,
                    false,
                )
            }
        runScreenTest(on, screen = {
            FriendMachineScreen(olegPress.id, OLEG.userId, {}, {}, {})
        }) {
            onNodeWithTag("friend-machine-tag-Ноги").assertTextEquals("Ноги")
            onAllNodesWithTag("photo-cover", useUnmergedTree = true).assertCountEquals(1)
            onNodeWithTag("stats-chart-title").assertDoesNotExist()
            onNodeWithTag("stats-best-trophy", useUnmergedTree = true).assertExists()
            onNodeWithTag("stats-best").assertTextEquals("85 кг × 6")
            onNodeWithTag("stats-history-date-0").assertTextEquals("13 ноября")
            onNodeWithTag("stats-history-results-0").assertTextEquals("80-85кг 8-6")
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
            onNodeWithTag("stats-history-0").assertHasNoClickAction()
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
