package monster.greyde.kachalochka.ui.visit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.width
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.friends.olegTrainedOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class VisitScreenTest {
    private val gym = FakeGym()
    private val visit =
        Visit(VisitId.random(), null, gym.today, gym.clock.current, gym.clock.current, false)
    private val press = Machine.new("Жим ногами", null, gym.clock.current)
    private val recorded =
        WorkoutSet(
            WorkoutSetId.random(),
            null,
            visit.id,
            press.id,
            70.0,
            10,
            0,
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
            shared.today,
            shared.clock.current,
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
            onNodeWithTag("top-bar-title").assertTextEquals("Сегодня")
            onNodeWithTag("end-visit").assertDoesNotExist()
            onNodeWithTag("visit-set-count").assertTextEquals("1 ПОДХОД")
            onNodeWithTag("set-sheet").assertDoesNotExist()
            onNodeWithTag("pick-machine").performScrollTo().performClick()
            waitForIdle()
            assertEquals(1, picks)
        }
    }

    @Test
    fun sharing_the_visit_hands_its_text_over_and_shows_the_notice_until_tapped() {
        runScreenTest(gym, screen = { visitScreen() }) {
            waitForIdle()
            onNodeWithTag("share-visit").performClick()
            waitForIdle()

            assertEquals(1, gym.texts.shared.size)
            onNodeWithTag("visit-notice").assertTextEquals("Скопировано")

            onNodeWithTag("visit-notice").performClick()
            waitForIdle()
            onNodeWithTag("visit-notice").assertDoesNotExist()
        }
    }

    @Test
    fun a_machine_row_names_the_machine_and_its_note_above_its_results() {
        runBlocking { gym.machines.upsert(press.copy(setupNote = "Сиденье на 4")) }
        runScreenTest(gym, screen = { visitScreen() }) {
            waitForIdle()
            val id = press.id.value
            onNodeWithTag(
                "group-note-$id",
                useUnmergedTree = true,
            ).assertTextEquals("Сиденье на 4")
            onNodeWithTag(
                "group-summary-$id",
                useUnmergedTree = true,
            ).assertTextEquals("70кг 1x10")
            val title = onNodeWithTag("group-title-$id", useUnmergedTree = true).getBoundsInRoot()
            val summary =
                onNodeWithTag("group-summary-$id", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(summary.top >= title.bottom)
        }
    }

    @Test
    fun a_long_name_takes_the_room_a_short_note_leaves() {
        val name = "Жим ногами в раме Смита с широкой постановкой стоп ".repeat(3).trim()
        runBlocking { gym.machines.upsert(press.copy(name = name, setupNote = "4")) }
        runScreenTest(gym, screen = { visitScreen() }) {
            waitForIdle()
            val id = press.id.value
            val row = onNodeWithTag("group-$id").getBoundsInRoot()
            val title = onNodeWithTag("group-title-$id", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(title.width > (row.width * 0.6f), "title ${title.width} of ${row.width}")
        }
    }

    @Test
    fun grouping_by_tag_heads_each_section_with_its_tags() {
        runBlocking { gym.machines.upsert(press.copy(tags = setOf("Ноги"))) }
        runScreenTest(gym, screen = { visitScreen() }) {
            waitForIdle()
            val id = press.id.value
            onNodeWithTag("group-tag-$id-Ноги", useUnmergedTree = true).assertTextEquals("Ноги")
            onNodeWithTag("section-Ноги").assertDoesNotExist()

            onNodeWithTag("group-by-tag").performClick()
            waitForIdle()

            onNodeWithTag("section-Ноги").assertTextEquals("Ноги")
        }
    }

    @Test
    fun a_visit_without_tags_offers_no_grouping() {
        runScreenTest(gym, screen = { visitScreen() }) {
            waitForIdle()
            onNodeWithTag("group-by-tag").assertDoesNotExist()
        }
    }

    @Test
    fun a_visit_without_sets_offers_no_share() {
        runScreenTest(gym, screen = { visitScreen(day = CalendarDay(2023, 11, 12)) }) {
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визит · 12 ноября")
            onNodeWithTag("share-visit").assertDoesNotExist()
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
            onNodeWithTag("pick-machine").assertTextEquals("Новое упражнение")

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
    fun a_comment_typed_in_the_sheet_shows_in_the_set_s_row() {
        runScreenTest(gym, screen = { visitScreen(picked = press.id) }) {
            waitForIdle()
            onNodeWithTag("set-comment-field").assertDoesNotExist()
            onNodeWithTag("set-comment").performClick()
            waitForIdle()
            onNodeWithTag("set-comment-field").performTextReplacement("Тяжело")
            onNodeWithTag("save-set").performClick()
            waitForIdle()

            val saved =
                gym.sets.rows.values
                    .single { it.id != recorded.id }
            onNodeWithTag("group-${press.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("set-comment-${saved.id.value}", useUnmergedTree = true)
                .assertTextEquals("Тяжело")
        }
    }

    @Test
    fun a_weight_typed_with_a_comma_is_saved() {
        runScreenTest(gym, screen = { visitScreen(picked = press.id) }) {
            waitForIdle()
            onNodeWithTag("weight-value").performTextReplacement("22,5")
            waitForIdle()
            onNodeWithTag("save-set").performClick()
            waitForIdle()
        }
        val weights = runBlocking { gym.sets.forVisit(visit.id) }.map { it.weight }
        assertEquals(setOf(70.0, 22.5), weights.toSet())
    }

    @Test
    fun a_weight_that_is_not_a_number_disables_saving() {
        runScreenTest(gym, screen = { visitScreen(picked = press.id) }) {
            waitForIdle()
            onNodeWithTag("weight-value").performTextReplacement("abc")
            waitForIdle()
            onNodeWithTag("save-set").assertIsNotEnabled()
        }
    }

    @Test
    fun the_save_button_takes_no_taps_until_the_set_is_saved() {
        runScreenTest(gym, screen = { visitScreen(picked = press.id) }) {
            waitForIdle()
            val gate = CompletableDeferred<Unit>().also { gym.sets.gate = it }
            onNodeWithTag("save-set").performClick()
            waitForIdle()
            onNodeWithTag("save-set").assertIsNotEnabled()
            onNode(saveProgress, useUnmergedTree = true).assertExists()

            gate.complete(Unit)
            waitForIdle()
            onNodeWithTag("save-set").assertIsEnabled()
            onNode(saveProgress, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithTag("visit-set-count").assertTextEquals("2 ПОДХОДА")
        }
    }

    @Test
    fun a_pound_machine_is_recorded_in_pounds_with_kilograms_in_brackets() {
        val cable =
            Machine
                .new("Кроссовер", null, gym.clock.current)
                .copy(unit = WeightUnit.Lb, weightStep = 5.0)
        runBlocking {
            gym.machines.upsert(cable)
            gym.sets.upsert(
                recorded.copy(id = WorkoutSetId.random(), machineId = cable.id, weight = 90.0),
            )
        }
        runScreenTest(gym, screen = { visitScreen(picked = cable.id) }) {
            waitForIdle()
            onNodeWithTag("weight-value").assertTextEquals("90")
            onNodeWithText("lb (41кг) всего · ±5lb (2.3кг)").assertIsDisplayed()
        }
    }

    @Test
    fun another_day_is_titled_with_its_date() {
        runScreenTest(gym, screen = { visitScreen(day = CalendarDay(2023, 11, 12)) }) {
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Визит · 12 ноября")
        }
    }

    @Test
    fun top_bar_back_closes_the_sheet_before_it_leaves() {
        var backs = 0
        runScreenTest(gym, screen = { visitScreen(onBack = { backs++ }) }) {
            onNodeWithTag("group-${press.id.value}").performClick()
            onNodeWithTag("set-row-${recorded.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("delete-set").assertIsDisplayed()

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("set-sheet").assertDoesNotExist()
            onNodeWithTag("sheet-peek").assertDoesNotExist()
            assertEquals(0, backs)

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            assertEquals(1, backs)
        }
    }

    @Test
    fun system_back_closes_the_sheet() {
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
            onNodeWithTag("set-sheet").assertDoesNotExist()
            assertEquals(0, backs)
        }
    }

    @Test
    fun swiping_down_closes_the_sheet() {
        runScreenTest(gym, screen = { visitScreen(picked = press.id) }) {
            waitForIdle()
            onNodeWithTag("set-sheet").performTouchInput { swipeDown() }
            waitForIdle()
            onNodeWithTag("set-sheet").assertDoesNotExist()
            onNodeWithTag("sheet-peek").assertDoesNotExist()
        }
    }

    @Test
    fun an_expanded_machine_adds_its_next_set_from_its_own_row() {
        runScreenTest(gym, screen = { visitScreen() }) {
            waitForIdle()
            onNodeWithTag("add-set-${press.id.value}").assertDoesNotExist()
            onNodeWithTag("group-${press.id.value}").performClick()
            waitForIdle()

            onNodeWithTag("add-set-${press.id.value}").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("set-sheet").assertIsDisplayed()
            onNodeWithTag("sheet-set-number", useUnmergedTree = true).assertTextEquals("подход 2")
        }
    }

    @Test
    fun ordering_hides_the_add_set_rows() {
        runScreenTest(gym, screen = { visitScreen() }) {
            waitForIdle()
            onNodeWithTag("group-${press.id.value}").performClick()
            onNodeWithTag("reorder-toggle").performClick()
            waitForIdle()
            onNodeWithTag("add-set-${press.id.value}").assertDoesNotExist()
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
            screen = { visitScreen(day = shared.today, picked = sharedPress.id) },
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
                    day = shared.today,
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

    @Test
    fun a_machine_s_picture_in_the_list_opens_its_settings() {
        val opened = mutableListOf<MachineId>()
        runScreenTest(gym, screen = { visitScreen(onOpenMachineSettings = { opened += it }) }) {
            waitForIdle()
            onNodeWithTag("thumb-${press.id.value}").performClick()
            waitForIdle()
        }
        assertEquals(listOf(press.id), opened)
    }

    @Test
    fun the_sheet_s_picture_opens_the_machine_s_settings() {
        val opened = mutableListOf<MachineId>()
        runScreenTest(
            gym,
            screen = {
                visitScreen(picked = press.id, onOpenMachineSettings = { opened += it })
            },
        ) {
            waitForIdle()
            onNodeWithTag("sheet-thumb").performClick()
            waitForIdle()
        }
        assertEquals(listOf(press.id), opened)
    }

    @Test
    fun order_mode_drags_a_machine_by_its_handle_and_opens_no_set() {
        val row = Machine.new("Тяга", null, gym.clock.current)
        val curl = Machine.new("Сгибание рук", null, gym.clock.current)
        val pulled = recorded.copy(id = WorkoutSetId.random(), machineId = row.id)
        val curled = recorded.copy(id = WorkoutSetId.random(), machineId = curl.id)
        runBlocking {
            gym.machines.upsert(row)
            gym.machines.upsert(curl)
            gym.sets.upsert(pulled.copy(recordedAt = gym.clock.current + 1.minutes))
            gym.sets.upsert(curled.copy(recordedAt = gym.clock.current + 2.minutes))
        }
        runScreenTest(gym, screen = { visitScreen() }) {
            onNodeWithTag("reorder-toggle").assertTextEquals("Порядок").performClick()
            waitForIdle()
            onNodeWithText("Готово").assertExists()
            listOf(press, row, curl).forEach {
                onNodeWithTag("drag-machine-${it.id.value}").assertExists()
            }
            onNodeWithTag("drag-set-${recorded.id.value}").assertExists()
            onAllNodes(hasTestTagStartingWith("machine-up-")).assertCountEquals(0)
            onAllNodes(hasTestTagStartingWith("set-up-")).assertCountEquals(0)

            onNodeWithTag("drag-machine-${curl.id.value}").performTouchInput {
                down(center)
                repeat(20) { moveBy(Offset(0f, -30f)) }
                up()
            }
            waitForIdle()
            assertTrue(
                onNodeWithTag("group-${curl.id.value}").fetchSemanticsNode().positionInRoot.y <
                    onNodeWithTag("group-${press.id.value}").fetchSemanticsNode().positionInRoot.y,
            )
            onNodeWithTag("set-row-${recorded.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("delete-set").assertDoesNotExist()

            onNodeWithTag("reorder-toggle").performClick()
            waitForIdle()
            onNodeWithTag("reorder-toggle").assertTextEquals("Порядок")
            onNodeWithTag("drag-machine-${curl.id.value}").assertDoesNotExist()
        }
        assertEquals(
            mapOf(curl.id to 1, press.id to 2, row.id to 3),
            gym.sets.rows.values
                .associate { it.machineId to it.position },
        )
    }

    @Test
    fun order_mode_drags_a_set_below_the_next_one_on_its_machine() {
        val row = Machine.new("Тяга", null, gym.clock.current)
        val second =
            recorded.copy(id = WorkoutSetId.random(), recordedAt = gym.clock.current + 1.minutes)
        val pulled =
            recorded.copy(
                id = WorkoutSetId.random(),
                machineId = row.id,
                recordedAt = gym.clock.current + 2.minutes,
            )
        runBlocking {
            gym.machines.upsert(row)
            gym.sets.upsert(second)
            gym.sets.upsert(pulled)
        }
        runScreenTest(gym, screen = { visitScreen() }) {
            onNodeWithTag("reorder-toggle").performClick()
            waitForIdle()

            onNodeWithTag("drag-set-${recorded.id.value}").performTouchInput {
                down(center)
                repeat(20) { moveBy(Offset(0f, 30f)) }
                up()
            }
            waitForIdle()
            assertTrue(
                onNodeWithTag("set-row-${second.id.value}").fetchSemanticsNode().positionInRoot.y <
                    onNodeWithTag("set-row-${recorded.id.value}")
                        .fetchSemanticsNode()
                        .positionInRoot.y,
            )
        }
        assertEquals(
            mapOf(second.id to 1, recorded.id to 2, pulled.id to 3),
            gym.sets.rows.values
                .associate { it.id to it.position },
        )
    }

    private val saveProgress =
        hasAnyAncestor(hasTestTag("save-set")) and
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    private fun hasTestTagStartingWith(prefix: String) =
        SemanticsMatcher("test tag starts with $prefix") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }

    @Test
    fun friends_results_show_under_the_previous_visit() {
        val ivanId = checkNotNull(shared.accounts.activeId.value)
        shared.olegTrainedOn(sharedPress, Friend(ivanId, "Иван"))
        runScreenTest(
            shared,
            screen = { visitScreen(day = shared.today, picked = sharedPress.id) },
        ) {
            waitForIdle()
            onNodeWithTag("sheet-friends").assertIsDisplayed()
            onNodeWithText("Олег · вчера · 80-85кг 8-6").assertIsDisplayed()
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
        day: CalendarDay = gym.today,
        picked: MachineId? = null,
        onConsumed: () -> Unit = {},
        onPickMachine: (MachineId?) -> Unit = {},
        onOpenMachineSettings: (MachineId) -> Unit = {},
        onBack: () -> Unit = {},
    ) = VisitScreen(
        day = day,
        pickedMachineId = picked,
        onPickedMachineConsumed = onConsumed,
        onBack = onBack,
        onOpenSettings = {},
        onPickMachine = onPickMachine,
        onOpenMachineSettings = onOpenMachineSettings,
    )
}
