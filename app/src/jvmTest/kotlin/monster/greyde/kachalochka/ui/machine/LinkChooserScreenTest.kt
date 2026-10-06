package monster.greyde.kachalochka.ui.machine

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LinkChooserScreenTest {
    private val gym = signedInGym()
    private val press = Machine.new("Жим ногами", ME.userId, gym.clock.current)
    private val duplicate = Machine.new("Жим ногами 2", ME.userId, gym.clock.current)
    private val bench = Machine.new("Жим лёжа", OLEG.userId, gym.clock.current)

    init {
        runBlocking {
            gym.machines.upsert(press)
            gym.machines.upsert(duplicate)
        }
        gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        gym.friends.machines += bench
    }

    @Test
    fun an_own_machine_is_merged_only_after_a_confirmation() {
        val merged = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            LinkChooserScreen(press.id, {}, {}, onMerged = { merged += it }, onLinked = {})
        }) {
            onNodeWithTag("chooser-own-${duplicate.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("cancel-merge").performClick()
            waitForIdle()
            onNodeWithTag("confirm-merge").assertDoesNotExist()

            onNodeWithTag("chooser-own-${duplicate.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("confirm-merge").performClick()
            waitForIdle()
        }
        assertEquals(listOf(press.id), merged)
        assertTrue(runBlocking { gym.machines.byId(duplicate.id) }!!.deleted)
    }

    @Test
    fun the_dialog_suggests_a_machine_and_keeps_the_one_chosen() {
        val merged = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            LinkChooserScreen(press.id, {}, {}, onMerged = { merged += it }, onLinked = {})
        }) {
            onNodeWithTag("chooser-own-${duplicate.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("merge-suggested-${press.id.value}", useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithTag("merge-keep-${duplicate.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("confirm-merge").performClick()
            waitForIdle()
        }
        assertEquals(listOf(duplicate.id), merged)
        assertTrue(runBlocking { gym.machines.byId(press.id) }!!.deleted)
    }

    @Test
    fun any_machine_of_a_linked_group_can_be_chosen() {
        val (pashas, link) = linkedCopy(bench, PASHA.userId, gym.clock.current)
        gym.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        gym.friends.machines += pashas
        gym.friends.links += link
        var copyFrom: MachineId? = null
        runScreenTest(gym, screen = {
            LinkChooserScreen(press.id, {}, {}, onMerged = {}, onLinked = { copyFrom = it })
        }) {
            onNodeWithTag("clear-search").performClick()
            onNodeWithTag("copy-settings").performClick()
            waitForIdle()
            onNodeWithTag("chooser-group-${bench.id.value}").performScrollTo().assertIsDisplayed()
            onNodeWithTag("chooser-friend-${pashas.id.value}").performScrollTo().performClick()
            waitForIdle()
        }
        assertEquals(pashas.id, copyFrom)
    }

    @Test
    fun a_friend_s_machine_is_linked_at_once() {
        var linked = false
        var copyFrom: MachineId? = null
        runScreenTest(gym, screen = {
            LinkChooserScreen(press.id, {}, {}, onMerged = {}, onLinked = {
                linked = true
                copyFrom = it
            })
        }) {
            onNodeWithTag("machine-search").assertTextEquals(press.name)
            onNodeWithTag("clear-search").performClick()
            waitForIdle()
            onNodeWithTag("copy-settings").assertIsOff().performClick()
            waitForIdle()
            onNodeWithTag("chooser-friends").performScrollTo().assertIsDisplayed()
            onNodeWithTag("chooser-friend-${bench.id.value}").performScrollTo().performClick()
            waitForIdle()
        }
        assertTrue(linked)
        assertEquals(bench.id, copyFrom)
        assertEquals(
            bench.id,
            runBlocking { gym.machineLinks.all(ME.userId) }.single().linkedMachineId,
        )
    }
}
