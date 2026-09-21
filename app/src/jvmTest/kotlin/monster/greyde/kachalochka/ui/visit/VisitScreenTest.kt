package monster.greyde.kachalochka.ui.visit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class VisitScreenTest {
    private val gym = FakeGym()
    private val visit =
        Visit(VisitId.random(), null, gym.clock.current, null, gym.clock.current, false)
    private val press = Machine.new("Жим ногами", null, gym.clock.current)
    private val recorded =
        WorkoutSet(
            WorkoutSetId.random(),
            null,
            visit.id,
            press.id,
            70.0,
            10,
            gym.clock.current,
            gym.clock.current,
            false,
        )

    init {
        runBlocking {
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            gym.sets.upsert(recorded)
        }
    }

    @Test
    fun a_fresh_visit_asks_for_a_machine() {
        var picks = 0
        runScreenTest(gym, screen = { visitScreen(onPickMachine = { picks++ }) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Визит")
            onNodeWithTag("visit-set-count").assertTextEquals("1 ПОДХОД")
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
            onNodeWithTag(
                "sheet-machine-name",
                useUnmergedTree = true,
            ).assertTextEquals("Жим ногами")
            onNodeWithTag("weight-value").assertTextEquals("70")
            onNodeWithTag("set-comment").assertIsNotEnabled()
            onNodeWithTag("weight-plus").performClick()
            onNodeWithTag("save-set").performClick()
            waitForIdle()

            onNodeWithTag("visit-set-count").assertTextEquals("2 ПОДХОДА")
            onNodeWithTag("sheet-set-number", useUnmergedTree = true).assertTextEquals("подход 3")
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

    @Test
    fun tapping_a_set_opens_the_sheet_in_edit_mode_and_back_leaves_it() {
        var backs = 0
        runScreenTest(gym, screen = { visitScreen(onBack = { backs++ }) }) {
            onNodeWithTag("group-${press.id.value}").performClick()
            onNodeWithTag("set-row-${recorded.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("save-set").assertTextEquals("Сохранить")
            onNodeWithTag("delete-set").assertIsDisplayed()

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("machine-settings").assertIsDisplayed()
            assertEquals(0, backs)
        }
    }

    @Test
    fun system_back_while_editing_a_set_leaves_edit_mode() {
        var backs = 0
        val dispatcher = NavigationEventDispatcher()
        val systemBack = DirectNavigationEventInput().also(dispatcher::addInput)
        val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = dispatcher
            }
        runScreenTest(
            gym,
            screen = {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    visitScreen(onBack = { backs++ })
                }
            },
        ) {
            onNodeWithTag("group-${press.id.value}").performClick()
            onNodeWithTag("set-row-${recorded.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("delete-set").assertIsDisplayed()

            runOnIdle { systemBack.backCompleted() }
            waitForIdle()
            onNodeWithTag("machine-settings").assertIsDisplayed()
            assertEquals(0, backs)
        }
    }

    @Test
    fun without_a_second_account_the_sheet_shows_no_person_chips() {
        runScreenTest(gym, screen = { visitScreen(picked = press.id) }) {
            waitForIdle()
            onNodeWithTag("person-add").assertDoesNotExist()
            onNodeWithTag("save-set").assertTextEquals("Сохранить подход")
        }
    }

    @Test
    fun a_person_chip_switches_who_the_save_button_records_as() {
        val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
        val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
        val shared = FakeGym().withAccounts(ivan, misha, active = ivan)
        val now = shared.clock.current
        val ivanVisit = Visit(VisitId.random(), ivan.account.userId, now, null, now, false)
        val ivanPress = Machine.new("Жим ногами", ivan.account.userId, now)
        runBlocking {
            shared.visits.upsert(ivanVisit)
            shared.machines.upsert(ivanPress)
        }
        runScreenTest(
            shared,
            screen = { visitScreen(visitId = ivanVisit.id, picked = ivanPress.id) },
        ) {
            waitForIdle()
            onNodeWithTag("person-add").assertExists()
            onNodeWithTag("save-set").assertTextEquals("Сохранить · Иван")

            onNodeWithTag("person-${misha.account.userId.value}").performClick()
            waitForIdle()

            onNodeWithTag("save-set").assertTextEquals("Сохранить · Миша")
            onNodeWithTag("save-set").performClick()
            waitForIdle()
            assertEquals(
                misha.account.userId,
                shared.sets.rows.values
                    .single()
                    .userId,
            )
        }
    }

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(
        Account(UserId(id), "$name@example.test", name),
        "access",
        "refresh",
        gym.clock.current,
    )

    @Composable
    private fun visitScreen(
        visitId: VisitId = visit.id,
        picked: MachineId? = null,
        onConsumed: () -> Unit = {},
        onPickMachine: (MachineId?) -> Unit = {},
        onEnded: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = VisitScreen(
        visitId = visitId,
        pickedMachineId = picked,
        onPickedMachineConsumed = onConsumed,
        onBack = onBack,
        onOpenSettings = {},
        onPickMachine = onPickMachine,
        onOpenMachineSettings = {},
        onVisitEnded = onEnded,
    )
}
