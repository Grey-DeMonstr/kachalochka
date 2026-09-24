package monster.greyde.kachalochka.ui.visit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
import kotlin.test.assertNotNull

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

    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
    private val shared = FakeGym().withAccounts(ivan, misha, active = ivan)
    private val sharedVisit =
        Visit(
            VisitId.random(),
            ivan.account.userId,
            shared.clock.current,
            null,
            shared.clock.current,
            false,
        )
    private val sharedPress = Machine.new("Жим ногами", ivan.account.userId, shared.clock.current)

    init {
        runBlocking {
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            gym.sets.upsert(recorded)
            shared.visits.upsert(sharedVisit)
            shared.machines.upsert(sharedPress)
        }
    }

    @Test
    fun a_fresh_visit_asks_for_a_machine() {
        var picks = 0
        runScreenTest(gym, screen = { visitScreen(onPickMachine = { picks++ }) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Визит")
            onNodeWithTag("visit-set-count").assertTextEquals("1 ПОДХОД")
            onNodeWithTag("set-sheet").assertDoesNotExist()
            onNodeWithTag("pick-machine").performScrollTo().performClick()
            waitForIdle()
            assertEquals(1, picks)
        }
    }

    @Test
    fun the_list_offers_a_new_machine_while_one_is_open() {
        val picks = mutableListOf<MachineId?>()
        runScreenTest(
            gym,
            screen = { visitScreen(picked = press.id, onPickMachine = { picks += it }) },
        ) {
            waitForIdle()
            onNodeWithTag("pick-machine").assertTextEquals("Новый тренажёр")

            onNodeWithTag("pick-machine").performScrollTo().performClick()
            waitForIdle()

            assertEquals(listOf<MachineId?>(press.id), picks)
        }
    }

    @Test
    fun tapping_the_machine_name_does_nothing() {
        var picks = 0
        runScreenTest(
            gym,
            screen = { visitScreen(picked = press.id, onPickMachine = { picks++ }) },
        ) {
            waitForIdle()
            onNodeWithTag("sheet-machine").performClick()
            waitForIdle()

            assertEquals(0, picks)
            onNodeWithTag("save-set").assertIsDisplayed()
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
    fun an_ended_visit_shows_its_date_and_no_end_button() {
        val at = visit.recordedAt
        val ended = Visit(VisitId.random(), null, at, at, at, false)
        runBlocking { gym.visits.upsert(ended) }
        runScreenTest(gym, screen = { visitScreen(visitId = ended.id) }) {
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визит · 14 ноября")
            onNodeWithTag("end-visit").assertDoesNotExist()
        }
    }

    @Test
    fun top_bar_back_collapses_the_sheet_before_it_leaves() {
        var backs = 0
        runScreenTest(gym, screen = { visitScreen(onBack = { backs++ }) }) {
            onNodeWithTag("group-${press.id.value}").performClick()
            onNodeWithTag("set-row-${recorded.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("delete-set").assertIsDisplayed()

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("delete-set").assertDoesNotExist()
            onNodeWithTag("save-set").assertDoesNotExist()
            onNodeWithTag("sheet-peek-label", useUnmergedTree = true)
                .assertTextEquals("Жим ногами · подход 2")
            assertEquals(0, backs)

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            assertEquals(1, backs)
        }
    }

    @Test
    fun system_back_collapses_the_sheet() {
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
                    visitScreen(picked = press.id, onBack = { backs++ })
                }
            },
        ) {
            waitForIdle()
            onNodeWithTag("save-set").assertIsDisplayed()

            runOnIdle { systemBack.backCompleted() }
            waitForIdle()
            onNodeWithTag("save-set").assertDoesNotExist()
            onNodeWithTag("sheet-peek").assertIsDisplayed()
            assertEquals(0, backs)
        }
    }

    @Test
    fun tapping_the_collapsed_bar_opens_the_sheet_again() {
        runScreenTest(gym, screen = { visitScreen(picked = press.id) }) {
            waitForIdle()
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithTag("sheet-peek").performClick()
            waitForIdle()
            onNodeWithTag("save-set").assertIsDisplayed()
            onNodeWithTag("sheet-peek").assertDoesNotExist()
        }
    }

    @Test
    fun swiping_down_collapses_the_sheet_and_swiping_the_bar_up_opens_it() {
        runScreenTest(gym, screen = { visitScreen(picked = press.id) }) {
            waitForIdle()
            onNodeWithTag("set-sheet").performTouchInput { swipeDown() }
            waitForIdle()
            onNodeWithTag("save-set").assertDoesNotExist()
            onNodeWithTag("sheet-peek").assertIsDisplayed()

            onNodeWithTag("sheet-peek").performTouchInput { swipeUp() }
            waitForIdle()
            onNodeWithTag("save-set").assertIsDisplayed()
        }
    }

    @Test
    fun a_short_slow_drag_leaves_the_sheet_open() {
        runScreenTest(gym, screen = { visitScreen(picked = press.id) }) {
            waitForIdle()
            onNodeWithTag("set-sheet").performTouchInput {
                swipeDown(startY = top + 10f, endY = top + 40f, durationMillis = 1_000)
            }
            waitForIdle()
            onNodeWithTag("save-set").assertIsDisplayed()
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
        runScreenTest(
            shared,
            screen = { visitScreen(visitId = sharedVisit.id, picked = sharedPress.id) },
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

    @Test
    fun machine_settings_never_open_another_account_s_machine() {
        var opened: MachineId? = null
        runScreenTest(
            shared,
            screen = {
                visitScreen(
                    visitId = sharedVisit.id,
                    picked = sharedPress.id,
                    onOpenMachineSettings = { opened = it },
                )
            },
        ) {
            onNodeWithTag("person-${misha.account.userId.value}").performClick()
            waitForIdle()
            onNodeWithTag("machine-settings").performClick()
            waitForIdle()
        }
        val mishaPress = shared.machines.rows.getValue(assertNotNull(opened))
        assertEquals(misha.account.userId, mishaPress.userId)
        assertEquals("Жим ногами", mishaPress.name)
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
        onOpenMachineSettings: (MachineId) -> Unit = {},
        onEnded: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = VisitScreen(
        visitId = visitId,
        pickedMachineId = picked,
        onPickedMachineConsumed = onConsumed,
        onBack = onBack,
        onOpenSettings = {},
        onPickMachine = onPickMachine,
        onOpenMachineSettings = onOpenMachineSettings,
        onVisitEnded = onEnded,
    )
}
