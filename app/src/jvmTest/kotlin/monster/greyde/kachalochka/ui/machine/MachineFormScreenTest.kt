package monster.greyde.kachalochka.ui.machine

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInRussian
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import monster.greyde.kachalochka.ui.strings.RuStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class MachineFormScreenTest {
    private val gym = FakeGym()

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(
        Account(UserId(id), "$name@example.test", name),
        "access",
        "refresh",
        gym.clock.current,
    )

    @Test
    fun the_form_saves_with_defaults() {
        val saved = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, ""), {}, {}, onSaved = { saved += it })
        }) {
            onNodeWithTag("save-machine").assertIsNotEnabled()
            onNodeWithTag("machine-statistics").assertDoesNotExist()
            onNodeWithTag("per-limb").assertDoesNotExist()
            onNodeWithTag("mode-total").assertIsSelected()
            onNodeWithTag("mode-counterweight-hint").assertDoesNotExist()
            onNodeWithTag("weight-step").assertTextEquals("2.5")

            onNodeWithTag("machine-name").performTextInput("Hack machine")
            onNodeWithTag("save-machine").performClick()
            waitForIdle()

            assertEquals(1, saved.size)
            val machine = runBlocking { gym.machines.byId(saved.single()) }
            assertEquals(2.5, machine?.weightStep)
        }
    }

    @Test
    fun a_saved_machine_opens_its_statistics() {
        val press = Machine.new("Leg press", null, gym.clock.current)
        runBlocking { gym.machines.upsert(press) }
        val opened = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            MachineFormScreen(
                MachineFormArgs(press.id, null, ""),
                {},
                {},
                onSaved = {},
                onOpenStatistics = { opened += it },
            )
        }) {
            onNodeWithTag("machine-statistics").performClick()
            waitForIdle()

            assertEquals(listOf(press.id), opened)
        }
    }

    @Test
    fun a_platform_typed_over_recorded_sets_recalculates_them_when_asked() {
        val t0 = gym.clock.current
        val press = Machine.new("Leg press", null, t0)
        val visit = VisitId.random()
        val recorded =
            WorkoutSet(WorkoutSetId.random(), null, visit, press.id, 70.0, 10, 0, t0, t0, false)
        runBlocking {
            gym.machines.upsert(press)
            gym.sets.upsert(recorded)
        }
        val saved = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            MachineFormScreen(
                MachineFormArgs(press.id, null, ""),
                {},
                {},
                onSaved = { saved += it },
            )
        }) {
            onNodeWithTag("platform-weight").performScrollTo().performTextReplacement("25")
            onNodeWithTag("save-machine").performClick()
            waitForIdle()
            assertEquals(emptyList(), saved)

            onNodeWithTag("recalculate-sets").performClick()
            waitForIdle()
        }
        assertEquals(listOf(press.id), saved)
        assertEquals(
            45.0,
            gym.sets.rows
                .getValue(recorded.id)
                .weight,
        )
    }

    /** A saved machine opened in the form, the system back, and the backs and saves asked for. */
    private class LeaveRun(
        val press: Machine,
    ) {
        var backs = 0
        val saved = mutableListOf<MachineId>()
        val dispatcher = NavigationEventDispatcher()
        val systemBack = DirectNavigationEventInput().also(dispatcher::addInput)
        val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = dispatcher
            }
    }

    private fun leaveRun(
        recorded: Boolean = false,
        block: ComposeUiTest.(LeaveRun) -> Unit,
    ) {
        val t0 = gym.clock.current
        val run = LeaveRun(Machine.new("Leg press", null, t0))
        runBlocking {
            gym.machines.upsert(run.press)
            if (recorded) {
                gym.sets.upsert(
                    WorkoutSet(
                        WorkoutSetId.random(),
                        null,
                        VisitId.random(),
                        run.press.id,
                        70.0,
                        10,
                        0,
                        t0,
                        t0,
                        false,
                    ),
                )
            }
        }
        runScreenTest(gym, screen = {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides run.owner) {
                MachineFormScreen(
                    MachineFormArgs(run.press.id, null, ""),
                    onBack = { run.backs++ },
                    onOpenSettings = {},
                    onSaved = { run.saved += it },
                )
            }
        }) { block(run) }
    }

    @Test
    fun back_from_an_untouched_form_leaves_at_once() =
        leaveRun { run ->
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            assertEquals(1, run.backs)
            onNodeWithTag("leave-save").assertDoesNotExist()
        }

    @Test
    fun system_back_from_an_untouched_form_leaves_at_once() =
        leaveRun { run ->
            waitForIdle()
            runOnIdle { run.systemBack.backCompleted() }
            waitForIdle()

            assertEquals(1, run.backs)
            onNodeWithTag("leave-save").assertDoesNotExist()
        }

    @Test
    fun system_back_with_edits_asks_first() =
        leaveRun { run ->
            onNodeWithTag("machine-note").performTextInput("Seat at 4")
            runOnIdle { run.systemBack.backCompleted() }
            waitForIdle()

            assertEquals(0, run.backs)
            onNodeWithText("Save changes?").assertExists()
        }

    @Test
    fun dismissing_the_question_stays_in_the_form() =
        leaveRun { run ->
            onNodeWithTag("machine-note").performTextInput("Seat at 4")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            runOnIdle { run.systemBack.backCompleted() }
            waitForIdle()

            onNodeWithTag("leave-save").assertDoesNotExist()
            assertEquals(0, run.backs)
            assertEquals(emptyList(), run.saved)
            onNodeWithTag("machine-note").assertTextEquals("Seat at 4")
        }

    @Test
    fun back_with_edits_asks_and_can_drop_them() =
        leaveRun { run ->
            onNodeWithTag("machine-note").performTextInput("Seat at 4")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            assertEquals(0, run.backs)
            onNodeWithText("Save changes?").assertExists()

            onNodeWithTag("leave-dont-save").assertTextEquals("Don't save").performClick()
            waitForIdle()

            assertEquals(1, run.backs)
            assertEquals(emptyList(), run.saved)
            assertEquals("", runBlocking { gym.machines.byId(run.press.id) }?.setupNote)
        }

    @Test
    fun saving_from_the_question_leaves_the_way_the_save_button_does() =
        leaveRun { run ->
            onNodeWithTag("machine-note").performTextInput("Seat at 4")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithTag("leave-save").assertTextEquals("Save").performClick()
            waitForIdle()

            assertEquals(listOf(run.press.id), run.saved)
            assertEquals(0, run.backs)
            assertEquals(
                "Seat at 4",
                runBlocking { gym.machines.byId(run.press.id) }?.setupNote,
            )
        }

    @Test
    fun saving_from_the_question_asks_about_recorded_sets_first() =
        leaveRun(recorded = true) { run ->
            onNodeWithTag("platform-weight").performScrollTo().performTextReplacement("25")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
            onNodeWithTag("leave-save").performClick()
            waitForIdle()
            assertEquals(emptyList(), run.saved)
            onNodeWithTag("leave-save").assertDoesNotExist()

            onNodeWithTag("keep-recorded-sets").performClick()
            waitForIdle()

            assertEquals(listOf(run.press.id), run.saved)
        }

    @Test
    fun a_form_that_cannot_be_saved_can_only_drop_its_edits() =
        leaveRun { run ->
            onNodeWithTag("machine-name").performTextReplacement("")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithTag("leave-save").assertIsNotEnabled()
            onNodeWithTag("leave-dont-save").assertIsEnabled()
        }

    @Test
    fun the_leave_question_speaks_russian() {
        runScreenTestInRussian(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, "Hack squat"), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("machine-note").performTextInput("Seat at 4")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithText(RuStrings.saveChangesTitle).assertExists()
            onNodeWithTag("leave-save").assertTextEquals(RuStrings.save)
            onNodeWithTag("leave-dont-save").assertTextEquals(RuStrings.dontSave)
        }
    }

    @Test
    fun tags_are_chosen_and_added_in_the_form() {
        runBlocking {
            gym.machines.upsert(
                Machine.new("Squat", null, gym.clock.current).copy(tags = setOf("Legs")),
            )
        }
        val saved = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, ""), {}, {}, onSaved = { saved += it })
        }) {
            onNodeWithTag("machine-name").performTextInput("Hack squat")
            onNodeWithTag("suggested-tags").assertDoesNotExist()
            onNodeWithTag("tag-legs").performScrollTo().assertIsOff().performClick()
            onNodeWithTag("new-tag").performScrollTo().performTextInput("Press")
            onNodeWithTag("add-tag").performClick()
            waitForIdle()
            onNodeWithTag("tag-legs").assertIsOn()
            onNodeWithTag("tag-press").assertIsOn()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()

            val machine = runBlocking { gym.machines.byId(saved.single()) }
            assertEquals(setOf("legs", "press"), machine?.tags)
        }
    }

    @Test
    fun a_gravitron_explains_its_weight_and_is_saved() {
        val saved = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, ""), {}, {}, onSaved = { saved += it })
        }) {
            onNodeWithTag("machine-name").performTextInput("Gravitron")
            onNodeWithTag("mode-counterweight").performClick()
            waitForIdle()
            onNodeWithTag("mode-counterweight-hint")
                .assertTextEquals("The weight counts as negative: the less, the better.")
            onNodeWithTag("save-machine").performClick()
            waitForIdle()

            val machine = runBlocking { gym.machines.byId(saved.single()) }
            assertEquals(WeightMode.Counterweight, machine?.weightMode)
        }
    }

    @Test
    fun the_machine_form_speaks_russian() =
        runScreenTestInRussian(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, ""), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("mode-total").assertTextEquals(RuStrings.choiceTotal)
            onNodeWithTag("mode-counterweight").performClick()
            onNodeWithTag("mode-counterweight-hint").assertTextEquals(RuStrings.counterweightHint)
            onNodeWithTag("save-machine").assertTextEquals(RuStrings.save)
        }

    @Test
    fun a_friend_s_photo_can_be_made_the_cover_but_not_deleted() {
        val on = signedInGym()
        on.friends.group("Forest Street Gym", owner = OLEG, ME)
        val olegs = Machine.new("Leg press", OLEG.userId, on.clock.current)
        val (mine, link) = linkedCopy(olegs, ME.userId, on.clock.current)
        val olegsPhoto = Photo.new(olegs.id, OLEG.userId, on.clock.current)
        runBlocking {
            on.machines.upsert(mine)
            on.machineLinks.upsert(link)
            on.photos.add(Photo.new(mine.id, ME.userId, on.clock.current), byteArrayOf(1))
        }
        on.friends.machines += olegs
        on.friends.photos += olegsPhoto
        var saved: MachineId? = null
        runScreenTest(on, screen = {
            MachineFormScreen(MachineFormArgs(mine.id, null, ""), {}, {}, onSaved = { saved = it })
        }) {
            onAllNodesWithTag("photo-thumbnail")[1].performClick()
            onNodeWithTag("delete-photo").assertDoesNotExist()
            onNodeWithTag("make-cover").assertTextEquals("Make it the cover").performClick()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()
        }
        assertEquals(mine.id, saved)
        assertEquals(olegsPhoto.id, runBlocking { on.machines.byId(mine.id)?.coverPhoto })
    }

    @Test
    fun a_swipe_in_the_opened_photo_shows_the_next_one() {
        val on = signedInGym()
        val machine = Machine.new("Leg press", ME.userId, on.clock.current)
        val first = Photo.new(machine.id, ME.userId, on.clock.current)
        val second = Photo.new(machine.id, ME.userId, on.clock.current + 1.minutes)
        runBlocking {
            on.machines.upsert(machine.copy(coverPhoto = first.id))
            on.photos.add(first, byteArrayOf(1))
            on.photos.add(second, byteArrayOf(2))
        }
        runScreenTest(on, screen = {
            MachineFormScreen(MachineFormArgs(machine.id, null, ""), {}, {}, onSaved = {})
        }) {
            onAllNodesWithTag("photo-thumbnail")[0].performClick()
            waitForIdle()
            onNodeWithTag("make-cover").assertDoesNotExist()

            onNodeWithTag("photo-viewer").performTouchInput { swipeLeft() }
            waitForIdle()
            onNodeWithTag("make-cover").performClick()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()
        }
        assertEquals(second.id, runBlocking { on.machines.byId(machine.id)?.coverPhoto })
    }

    @Test
    fun an_opened_photo_can_be_shared() {
        val on = signedInGym()
        val machine = Machine.new("Leg press", ME.userId, on.clock.current)
        runBlocking {
            on.machines.upsert(machine)
            on.photos.add(Photo.new(machine.id, ME.userId, on.clock.current), byteArrayOf(3))
        }
        runScreenTest(on, screen = {
            MachineFormScreen(MachineFormArgs(machine.id, null, ""), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("photo-thumbnail").performClick()
            waitForIdle()
            onNodeWithTag("share-photo").performClick()
            waitForIdle()
        }
        assertEquals(
            listOf(3.toByte()),
            on.photoShares.shared
                .single()
                .toList(),
        )
    }

    @Test
    fun a_photo_taken_in_the_form_is_saved_with_the_machine() {
        val saved = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            MachineFormScreen(
                MachineFormArgs(null, null, "Hack squat"),
                {},
                {},
                onSaved = { saved += it },
            )
        }) {
            onAllNodesWithTag("photo-thumbnail").assertCountEquals(0)

            onNodeWithTag("machine-photo").performClick()
            onNodeWithTag("take-photo").performClick()
            waitForIdle()
            onNodeWithTag("machine-photo").performClick()
            onNodeWithTag("pick-photo").performClick()
            waitForIdle()

            onAllNodesWithTag("photo-thumbnail").assertCountEquals(2)
            onNodeWithTag("save-machine").performClick()
            waitForIdle()
        }
        assertEquals(2, runBlocking { gym.photos.forMachine(saved.single()) }.size)
    }

    @Test
    fun an_opened_photo_can_be_removed_from_the_form() {
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, "Hack squat"), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("machine-photo").performClick()
            onNodeWithTag("take-photo").performClick()
            waitForIdle()

            onNodeWithTag("photo-thumbnail").performClick()
            waitForIdle()
            onNodeWithTag("photo-viewer").assertExists()
            onNodeWithTag("delete-photo").performClick()
            waitForIdle()

            onNodeWithTag("photo-viewer").assertDoesNotExist()
            onAllNodesWithTag("photo-thumbnail").assertCountEquals(0)
        }
    }

    @Test
    fun an_own_unit_asks_for_its_name_and_shows_it_by_the_platform_weight() {
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, "Gravitron"), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("unit-label").assertDoesNotExist()

            onNodeWithTag("unit-custom").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("unit-custom").assertIsSelected()
            onNodeWithTag("save-machine").assertIsNotEnabled()

            onNodeWithTag("unit-label").performTextInput("extremely long unit")
            waitForIdle()
            onNodeWithTag("unit-label").assertTextEquals("extremely lo")
            onNodeWithTag("platform-weight-unit", useUnmergedTree = true)
                .assertTextEquals("extremely lo")
            onNodeWithTag("save-machine").assertIsEnabled()
        }
    }

    @Test
    fun the_step_is_only_typed_and_the_typed_step_is_saved() {
        val saved = mutableListOf<MachineId>()
        runScreenTest(gym, screen = {
            MachineFormScreen(
                MachineFormArgs(null, null, "Hack squat"),
                {},
                {},
                onSaved = { saved += it },
            )
        }) {
            listOf("1", "2.5", "5", "10").forEach {
                onNodeWithTag("step-$it").assertDoesNotExist()
            }

            onNodeWithTag("weight-step").performTextReplacement("1,25")
            waitForIdle()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()
        }
        val machine = runBlocking { gym.machines.byId(saved.single()) }
        assertEquals(1.25, machine?.weightStep)
    }

    @Test
    fun the_form_has_no_title_and_names_its_empty_fields_inside_them() =
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, ""), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("")
            onNodeWithTag("machine-name-placeholder").assertTextEquals("Name")
            onNodeWithTag("machine-note-placeholder").assertTextEquals("Comment")
            onNodeWithText("Tags").assertDoesNotExist()
            onNodeWithText("After saving", substring = true).assertDoesNotExist()

            onNodeWithTag("machine-name").performTextInput("Hack squat")
            waitForIdle()
            onNodeWithTag("machine-name-placeholder").assertDoesNotExist()
        }

    @Test
    fun friends_tags_follow_the_own_ones_and_the_new_tag_field_comes_last() {
        val on = signedInGym()
        on.friends.group("Forest Street Gym", owner = OLEG, ME)
        on.friends.machines +=
            Machine.new("Press", OLEG.userId, on.clock.current).copy(tags = setOf("Shoulders"))
        runBlocking {
            on.machines.upsert(
                Machine.new("Squat", ME.userId, on.clock.current).copy(tags = setOf("Legs")),
            )
        }
        runScreenTest(on, screen = {
            MachineFormScreen(MachineFormArgs(null, null, "Hack squat"), {}, {}, onSaved = {})
        }) {
            val own = onNodeWithTag("tag-legs").getUnclippedBoundsInRoot()
            val friends = onNodeWithTag("friend-tag-shoulders").getUnclippedBoundsInRoot()
            val field = onNodeWithTag("new-tag").getUnclippedBoundsInRoot()
            assertTrue(own.right <= friends.left || own.bottom <= friends.top)
            assertTrue(friends.right <= field.left || friends.bottom <= field.top)
        }
    }

    @Test
    fun linked_machines_tags_are_suggested_first_and_a_tap_gives_one_to_the_machine() {
        val on = signedInGym()
        val now = on.clock.current
        val olegs = Machine.new("Leg press", OLEG.userId, now).copy(tags = setOf("Quads"))
        val (copy, link) = linkedCopy(olegs, ME.userId, now)
        val mine = copy.copy(tags = setOf("Legs"))
        on.friends.group("Forest Street Gym", owner = OLEG, ME)
        on.friends.machines += olegs
        runBlocking {
            on.machines.upsert(mine)
            on.machineLinks.upsert(link)
        }
        runScreenTest(on, screen = {
            MachineFormScreen(MachineFormArgs(mine.id, null, ""), {}, {}, onSaved = {})
        }) {
            val icon = onNodeWithTag("suggested-tags").getUnclippedBoundsInRoot()
            val suggested = onNodeWithTag("suggested-tag-quads").getUnclippedBoundsInRoot()
            val own = onNodeWithTag("tag-legs").getUnclippedBoundsInRoot()
            assertTrue(icon.right <= suggested.left)
            assertTrue(suggested.bottom <= own.top)

            onNodeWithTag("suggested-tag-quads").performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag("suggested-tags").assertDoesNotExist()
            onNodeWithTag("tag-quads").assertIsOn()
            onNodeWithTag("save-machine").performClick()
            waitForIdle()
        }
        assertEquals(setOf("legs", "quads"), runBlocking { on.machines.byId(mine.id)?.tags })
    }

    @Test
    fun the_menu_unlinks_only_after_a_confirmation() {
        val ivanSession = session("11111111-1111-4111-8111-111111111111", "Ivan")
        val signedIn = FakeGym().withAccounts(ivanSession, active = ivanSession)
        val now = signedIn.clock.current
        val (linked, link) =
            linkedCopy(
                Machine.new("Leg press", null, now),
                ivanSession.account.userId,
                now,
            )
        runBlocking {
            signedIn.machines.upsert(linked)
            signedIn.machineLinks.upsert(link)
        }
        runScreenTest(signedIn, screen = {
            MachineFormScreen(MachineFormArgs(linked.id, null, ""), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("machine-menu").performClick()
            onNodeWithTag("unlink-machine").performClick()
            waitForIdle()
            onNodeWithTag("cancel-unlink").performClick()
            waitForIdle()
            assertFalse(
                signedIn.machineLinks.rows
                    .getValue(link.id)
                    .deleted,
            )

            onNodeWithTag("machine-menu").performClick()
            onNodeWithTag("unlink-machine").performClick()
            waitForIdle()
            onNodeWithTag("confirm-unlink").performClick()
            waitForIdle()
        }
        assertTrue(
            signedIn.machineLinks.rows
                .getValue(link.id)
                .deleted,
        )
    }

    @Test
    fun the_menu_deletes_a_machine_without_sets_only_after_a_confirmation() {
        val press = Machine.new("Leg press", null, gym.clock.current)
        runBlocking { gym.machines.upsert(press) }
        var closed = 0
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(press.id, null, ""), { closed++ }, {}, onSaved = {})
        }) {
            onNodeWithTag("machine-menu").performClick()
            onNodeWithTag("delete-machine").assertTextEquals("Delete machine").performClick()
            waitForIdle()
            onNodeWithText("\"Leg press\" will disappear from the list.").assertExists()
            onNodeWithTag("cancel-delete-machine").performClick()
            waitForIdle()
            assertFalse(
                gym.machines.rows
                    .getValue(press.id)
                    .deleted,
            )

            onNodeWithTag("machine-menu").performClick()
            onNodeWithTag("delete-machine").performClick()
            waitForIdle()
            onNodeWithTag("confirm-delete-machine").performClick()
            waitForIdle()
        }
        assertTrue(
            gym.machines.rows
                .getValue(press.id)
                .deleted,
        )
        assertEquals(1, closed)
    }

    @Test
    fun a_machine_with_sets_is_not_offered_for_deletion() {
        val now = gym.clock.current
        val press = Machine.new("Leg press", null, now)
        val set =
            WorkoutSet(
                WorkoutSetId.random(),
                null,
                VisitId.random(),
                press.id,
                70.0,
                10,
                0,
                now,
                now,
                false,
            )
        runBlocking {
            gym.machines.upsert(press)
            gym.sets.upsert(set)
        }
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(press.id, null, ""), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("machine-menu").performClick()
            onNodeWithTag("delete-machine").assertDoesNotExist()
        }
    }

    @Test
    fun a_new_machine_has_no_menu() =
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(null, null, "Hack squat"), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("machine-menu").assertDoesNotExist()
            onNodeWithTag("link-machine").assertDoesNotExist()
        }

    @Test
    fun a_saved_machine_goes_into_a_chosen_plan_and_the_form_says_so() {
        val now = gym.clock.current
        val press = Machine.new("Leg press", null, now)
        val legs = Plan(PlanId.random(), null, "Legs", emptyList(), now, now, false)
        runBlocking {
            gym.machines.upsert(press)
            gym.plans.upsert(legs)
        }
        runScreenTest(gym, screen = {
            MachineFormScreen(MachineFormArgs(press.id, null, ""), {}, {}, onSaved = {})
        }) {
            onNodeWithTag("machine-menu").performClick()
            onNodeWithTag("unlink-machine").assertDoesNotExist()
            onNodeWithTag("machine-to-plan").performClick()
            waitForIdle()
            onNodeWithTag("plan-choice-new").assertTextEquals("New plan")
            onNodeWithTag("plan-choice-${legs.id.value}").assertTextEquals("Legs").performClick()
            waitForIdle()

            onNodeWithTag("plan-choice-new").assertDoesNotExist()
            onNodeWithTag("placement-notice").assertTextEquals("Added to “Legs”")
            assertEquals(
                listOf(press.id),
                gym.plans.rows
                    .getValue(legs.id)
                    .machineIds,
            )
            onNodeWithTag("placement-notice").performClick()
            waitForIdle()
            onNodeWithTag("placement-notice").assertDoesNotExist()
        }
    }

    @Test
    fun a_saved_machine_s_menu_opens_a_new_plan_or_today_s_visit_for_it() {
        val press = Machine.new("Leg press", null, gym.clock.current)
        runBlocking { gym.machines.upsert(press) }
        val newPlans = mutableListOf<MachineId>()
        val visits = mutableListOf<CalendarDay>()
        runScreenTest(gym, screen = {
            MachineFormScreen(
                MachineFormArgs(press.id, null, ""),
                {},
                {},
                onSaved = {},
                onNewPlan = { newPlans += it },
                onOpenVisit = { visits += it },
            )
        }) {
            onNodeWithTag("machine-menu").performClick()
            onNodeWithTag("machine-to-plan").performClick()
            waitForIdle()
            onNodeWithTag("plan-choice-new").performClick()
            waitForIdle()
            onNodeWithTag("machine-menu").performClick()
            onNodeWithTag("machine-to-today").assertTextEquals("Repeat today").performClick()
            waitForIdle()
        }
        assertEquals(listOf(press.id), newPlans)
        assertEquals(listOf(gym.today), visits)
        assertEquals(listOf(press.id), runBlocking { gym.visits.onDay(null, gym.today) }?.planned)
    }

    @Test
    fun a_saved_machine_offers_linking_and_names_the_machines_it_is_linked_with() {
        val signedIn = signedInGym()
        val now = signedIn.clock.current
        val olegs = Machine.new("Leg press", OLEG.userId, now)
        val (press, link) = linkedCopy(olegs, ME.userId, now)
        signedIn.friends.group("Forest Street Gym", owner = OLEG, ME)
        signedIn.friends.machines += olegs
        runBlocking {
            signedIn.machines.upsert(press)
            signedIn.machineLinks.upsert(link)
        }
        var linking = 0
        runScreenTest(signedIn, screen = {
            MachineFormScreen(
                MachineFormArgs(press.id, null, ""),
                {},
                {},
                onSaved = {},
                onLink = { linking++ },
            )
        }) {
            onNodeWithTag(
                "machine-linked-with-${olegs.id.value}",
            ).assertTextEquals("O", "Leg press")
            onNodeWithTag("link-machine").performScrollTo().performClick()
            waitForIdle()
        }
        assertEquals(1, linking)
    }

    @Test
    fun tapping_a_linked_machine_opens_it_as_its_owner_s() {
        val signedIn = signedInGym()
        val now = signedIn.clock.current
        val olegs = Machine.new("Leg press", OLEG.userId, now)
        val (press, link) = linkedCopy(olegs, ME.userId, now)
        val (pashas, pashaLink) = linkedCopy(olegs, PASHA.userId, now)
        signedIn.friends.group("Forest Street Gym", owner = OLEG, ME, PASHA)
        signedIn.friends.machines += listOf(olegs, pashas.copy(name = "Platform"))
        signedIn.friends.links += pashaLink
        runBlocking {
            signedIn.machines.upsert(press)
            signedIn.machineLinks.upsert(link)
        }
        val opened = mutableListOf<Pair<MachineId, UserId>>()
        runScreenTest(signedIn, screen = {
            MachineFormScreen(
                MachineFormArgs(press.id, null, ""),
                {},
                {},
                onSaved = {},
                onOpenFriendMachine = { machine, owner -> opened += machine to owner },
            )
        }) {
            onNodeWithTag(
                "machine-linked-with-${olegs.id.value}",
            ).assertTextEquals("O", "Leg press")
            onNodeWithTag("machine-linked-with-${pashas.id.value}")
                .assertTextEquals("P", "Platform")
                .performClick()
            waitForIdle()
        }
        assertEquals(listOf(pashas.id to PASHA.userId), opened)
    }
}
