package monster.greyde.kachalochka.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInEnglish
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalTestApi::class)
class CalendarScreenTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val sunday =
        Visit(VisitId.random(), null, CalendarDay(2023, 11, 12), t0 - 2.days, t0, false)

    private val press = Machine.new("Жим ногами", null, t0)

    init {
        runBlocking {
            gym.visits.upsert(sunday)
            gym.machines.upsert(press)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    sunday.id,
                    press.id,
                    70.0,
                    10,
                    0,
                    t0 - 2.days,
                    t0,
                    false,
                ),
            )
        }
    }

    @Composable
    private fun calendar(
        onBack: () -> Unit = {},
        onOpenVisit: (CalendarDay) -> Unit = {},
        onOpenFriendVisit: (UserId, String, CalendarDay) -> Unit = { _, _, _ -> },
        onSaveAsPlan: (CalendarDay) -> Unit = {},
    ) = CalendarScreen(
        onBack = onBack,
        onOpenSettings = {},
        onOpenVisit = onOpenVisit,
        onOpenFriendVisit = onOpenFriendVisit,
        onSaveAsPlan = onSaveAsPlan,
    )

    @Test
    fun the_visit_menu_s_new_plan_saves_the_visit_as_a_plan() {
        var saved: CalendarDay? = null
        runScreenTest(gym, screen = { calendar(onSaveAsPlan = { saved = it }) }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().performClick()
            onNodeWithTag("save-as-plan-${sunday.id.value}")
                .assertTextEquals("В план…")
                .performClick()
            waitForIdle()
            onNodeWithTag("plan-choice-new").performClick()
            waitForIdle()
        }
        assertEquals(CalendarDay(2023, 11, 12), saved)
    }

    @Test
    fun the_visit_menu_adds_the_visit_s_machines_to_a_chosen_plan_and_says_so() {
        val back = Plan(PlanId.random(), null, "Спина", emptyList(), t0, t0, false)
        runBlocking { gym.plans.upsert(back) }
        var saved: CalendarDay? = null
        runScreenTest(gym, screen = { calendar(onSaveAsPlan = { saved = it }) }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().performClick()
            onNodeWithTag("save-as-plan-${sunday.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("plan-choice-${back.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("placement-notice").assertTextEquals("Добавлено в «Спина»")
        }
        assertNull(saved)
        assertEquals(
            listOf(press.id),
            gym.plans.rows
                .getValue(back.id)
                .machineIds,
        )
    }

    @Test
    fun the_visit_menu_repeats_the_visit_today_and_opens_today() {
        var opened: CalendarDay? = null
        runScreenTest(gym, screen = { calendar(onOpenVisit = { opened = it }) }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().performClick()
            onNodeWithTag("repeat-today-${sunday.id.value}")
                .assertTextEquals("Повторить сегодня")
                .performClick()
            waitForIdle()
        }
        assertEquals(gym.today, opened)
    }

    @Test
    fun the_menu_of_today_s_visit_has_no_repeat() {
        val todayVisit = Visit(VisitId.random(), null, gym.today, t0, t0, false)
        runBlocking { gym.visits.upsert(todayVisit) }
        runScreenTest(gym, screen = { calendar() }) {
            waitForIdle()
            onNodeWithTag("visit-menu-${todayVisit.id.value}").performScrollTo().performClick()
            onNodeWithTag("save-as-plan-${todayVisit.id.value}").assertExists()
            onNodeWithTag("repeat-today-${todayVisit.id.value}").assertDoesNotExist()
        }
    }

    /** Иван signed in, with [trained] in his group each having a visit on the 12th. */
    private fun friendsGym(vararg trained: Friend): FakeGym {
        val signed = signedInGym()
        signed.friends.group("Зал на Лесной", owner = ME, *trained)
        trained.forEach {
            signed.friends.visits +=
                Visit(
                    VisitId.random(),
                    it.userId,
                    CalendarDay(2023, 11, 12),
                    t0 - 2.days,
                    t0,
                    false,
                )
        }
        return signed
    }

    @Test
    fun a_day_shows_a_dot_per_friend_and_a_friend_s_card_opens_their_visit() {
        val signed = friendsGym(OLEG, PASHA)
        var opened: Triple<UserId, String, CalendarDay>? = null
        runScreenTest(signed, screen = {
            calendar(onOpenFriendVisit = { id, name, day -> opened = Triple(id, name, day) })
        }) {
            onNodeWithTag("day-dot-friend-2023-11-12-0", useUnmergedTree = true).assertExists()
            onNodeWithTag("day-dot-friend-2023-11-12-1", useUnmergedTree = true).assertExists()
            onNodeWithTag("day-dot-friend-2023-11-12-2", useUnmergedTree = true)
                .assertDoesNotExist()
            onNodeWithTag("day-dot-friend-2023-11-11-0", useUnmergedTree = true)
                .assertDoesNotExist()

            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("friend-visit-${OLEG.userId.value}").performScrollTo().performClick()
            waitForIdle()
        }
        assertEquals(Triple(OLEG.userId, "Олег", CalendarDay(2023, 11, 12)), opened)
    }

    @Test
    fun a_day_draws_at_most_four_dots_the_own_one_first() {
        val anna = Friend(UserId("55555555-5555-4555-8555-555555555555"), "Анна")
        val misha = Friend(UserId("66666666-6666-4666-8666-666666666666"), "Миша")
        val signed = friendsGym(OLEG, PASHA, anna, misha)
        runBlocking {
            signed.visits.upsert(
                Visit(
                    VisitId.random(),
                    ME.userId,
                    CalendarDay(2023, 11, 12),
                    t0 - 2.days,
                    t0,
                    false,
                ),
            )
        }
        runScreenTest(signed, screen = { calendar() }) {
            onNodeWithTag("day-dot-friend-2023-11-12-2", useUnmergedTree = true).assertExists()
            onNodeWithTag("day-dot-friend-2023-11-12-3", useUnmergedTree = true)
                .assertDoesNotExist()
        }
    }

    @Test
    fun the_calendar_screen_speaks_english() =
        runScreenTestInEnglish(FakeGym(), screen = { calendar() }) {
            waitForIdle()
            onNodeWithTag("top-bar-title").assertTextEquals("Visits")
            onNodeWithTag("calendar-empty").performScrollTo().assertTextEquals("No visit")
        }

    @Test
    fun a_marked_day_lists_its_visit_and_opens_it() {
        var opened: CalendarDay? = null
        runScreenTest(gym, screen = { calendar(onOpenVisit = { opened = it }) }) {
            onNodeWithTag("calendar-month").assertTextEquals("Ноябрь 2023")
            onNodeWithTag("day-2023-11-15").assertIsNotEnabled()
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("calendar-visit-${sunday.id.value}").performScrollTo().performClick()
            waitForIdle()
            assertEquals(CalendarDay(2023, 11, 12), opened)
        }
    }

    @Test
    fun a_visit_card_draws_its_tags_in_capitals() {
        val press = Machine.new("Жим ногами", null, t0).copy(tags = setOf("Ноги", "Жим"))
        runBlocking {
            gym.machines.upsert(press)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    sunday.id,
                    press.id,
                    80.0,
                    8,
                    0,
                    t0,
                    t0,
                    false,
                ),
            )
        }
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            val card = "calendar-visit-${sunday.id.value}"
            onNodeWithTag("$card-tag-жим", useUnmergedTree = true).assertTextEquals("ЖИМ")
            onNodeWithTag("$card-tag-ноги", useUnmergedTree = true).assertTextEquals("НОГИ")
        }
    }

    @Test
    fun a_card_shows_three_machines_and_the_rest_on_request() {
        val names = listOf("Гиперэкстензия", "Тяга", "Бабочка", "Приседания")
        runBlocking {
            names.forEachIndexed { i, name ->
                val machine = Machine.new(name, null, t0)
                gym.machines.upsert(machine)
                gym.sets.upsert(
                    WorkoutSet(
                        WorkoutSetId.random(),
                        null,
                        sunday.id,
                        machine.id,
                        20.0,
                        10,
                        i + 1,
                        t0,
                        t0,
                        false,
                    ),
                )
            }
        }
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            val card = "calendar-visit-${sunday.id.value}"
            onNodeWithTag("$card-machine-2", useUnmergedTree = true).assertExists()
            onNodeWithTag("$card-machine-3", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithTag("$card-more")
                .performScrollTo()
                .assertTextEquals("Ещё 2 упражнения")
                .performClick()
            waitForIdle()
            onNodeWithTag("$card-machine-4", useUnmergedTree = true).assertExists()
            onNodeWithTag("$card-more").assertTextEquals("Свернуть")
        }
    }

    @Test
    fun the_feed_lists_the_visits_and_the_switch_is_kept() {
        var opened: CalendarDay? = null
        runScreenTest(gym, screen = { calendar(onOpenVisit = { opened = it }) }) {
            onNodeWithTag("mode-feed").performClick()
            waitForIdle()
            onNodeWithTag("feed-day-2023-11-12").assertTextEquals("Воскресенье, 12 ноября")
            onNodeWithTag("feed-month-2023-11-12", useUnmergedTree = true)
                .assertTextEquals("НОЯБРЬ 2023")
            onNodeWithTag("feed-end", useUnmergedTree = true).assertTextEquals("Это первый визит")
            onNodeWithTag("feed-visit-${sunday.id.value}").performClick()
            waitForIdle()
        }
        assertEquals(CalendarDay(2023, 11, 12), opened)
        assertEquals(VisitsMode.Feed, gym.visitsModes.mode.value)
    }

    @Test
    fun the_feed_s_people_hide_and_show_their_cards() {
        val anna = Friend(UserId("55555555-5555-4555-8555-555555555555"), "Анна Карпелевич")
        val signed = friendsGym(OLEG, anna)
        runBlocking { signed.visitsModes.set(VisitsMode.Feed) }
        val oleg = "feed-person-${OLEG.userId.value}"
        val olegCard = "feed-friend-${OLEG.userId.value}-2023-11-12"
        runScreenTest(signed, screen = { calendar() }) {
            waitForIdle()
            onNodeWithTag("feed-person-${anna.userId.value}-name", useUnmergedTree = true)
                .assertTextEquals("Анна")
            onNodeWithTag(oleg).assertIsOn()
            onNodeWithTag(olegCard).assertExists()

            onNodeWithTag(oleg).performClick()
            waitForIdle()
            onNodeWithTag(oleg).assertIsOff()
            onNodeWithTag(olegCard).assertDoesNotExist()

            onNodeWithTag("feed-person-${anna.userId.value}").performClick()
            onNodeWithTag("feed-person-${ME.userId.value}").performClick()
            waitForIdle()
            onNodeWithTag("feed-nobody", useUnmergedTree = true)
                .assertTextEquals("Выберите хотя бы одного человека")
        }
        assertEquals(3, signed.feedPeople.hidden.value.size)
    }

    @Test
    fun moving_from_the_feed_opens_the_calendar_on_the_visit_s_day() {
        runBlocking { gym.visitsModes.set(VisitsMode.Feed) }
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("visit-menu-${sunday.id.value}").performClick()
            onNodeWithTag("move-visit-${sunday.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("move-banner").assertIsDisplayed()
            onNodeWithTag("calendar-day-title").assertTextEquals("Воскресенье, 12 ноября")
        }
        assertEquals(VisitsMode.Calendar, gym.visitsModes.mode.value)
    }

    @Test
    fun days_are_round_and_a_visit_mark_keeps_the_number_in_place() {
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12")
                .assertWidthIsEqualTo(42.dp)
                .assertHeightIsEqualTo(42.dp)
            val plain = onNodeWithText("11", useUnmergedTree = true).getBoundsInRoot()
            val marked = onNodeWithText("12", useUnmergedTree = true).getBoundsInRoot()
            assertEquals(plain.top, marked.top)
        }
    }

    @Test
    fun the_mode_selector_keeps_clear_of_the_top_bar() {
        runScreenTest(gym, screen = { calendar() }) {
            val bar = onNodeWithTag("account-avatar").getBoundsInRoot()
            val selector = onNodeWithTag("mode-calendar").getBoundsInRoot()
            // The bar's 14 dp bottom padding, then 12 dp above the selector.
            assert(selector.top - bar.bottom >= 26.dp) { "${selector.top - bar.bottom}" }
        }
    }

    @Test
    fun removing_asks_for_confirmation_first() {
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().performClick()
            onNodeWithTag("remove-visit-${sunday.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("cancel-remove").performClick()
            waitForIdle()
            assertEquals(
                false,
                gym.visits.rows
                    .getValue(sunday.id)
                    .deleted,
            )

            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().performClick()
            onNodeWithTag("remove-visit-${sunday.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("confirm-remove").performClick()
            waitForIdle()
            assertEquals(
                true,
                gym.visits.rows
                    .getValue(sunday.id)
                    .deleted,
            )
            onNodeWithTag("calendar-empty").performScrollTo().assertIsDisplayed()
            onNodeWithTag("calendar-empty").assertTextEquals("Нет визита")
        }
    }

    @Test
    fun a_visit_moves_to_the_day_tapped_after_move() {
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().performClick()
            onNodeWithTag("move-visit-${sunday.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("move-banner").assertIsDisplayed()

            onNodeWithTag("day-2023-11-05").performScrollTo().performClick()
            waitForIdle()

            onNodeWithTag("move-banner").assertDoesNotExist()
            val moved = assertNotNull(gym.visits.rows[sunday.id])
            assertEquals(CalendarDay(2023, 11, 5), CalendarDay.of(moved.recordedAt, Duration.ZERO))
            assertEquals(CalendarDay(2023, 11, 5), moved.day)
        }
    }

    @Test
    fun a_move_offers_no_other_edits_and_can_be_cancelled() {
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().performClick()
            onNodeWithTag("move-visit-${sunday.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("visit-menu-${sunday.id.value}").assertDoesNotExist()

            onNodeWithTag("cancel-move").performClick()
            waitForIdle()

            onNodeWithTag("move-banner").assertDoesNotExist()
            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().assertIsDisplayed()
            assertEquals(sunday, gym.visits.rows[sunday.id])
        }
    }

    @Test
    fun back_while_moving_cancels_the_move() {
        var backs = 0
        runScreenTest(gym, screen = { calendar(onBack = { backs++ }) }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().performClick()
            onNodeWithTag("move-visit-${sunday.id.value}").performClick()
            waitForIdle()

            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()

            onNodeWithTag("move-banner").assertDoesNotExist()
            assertEquals(0, backs)
        }
    }

    @Test
    fun a_past_day_offers_to_add_a_visit_and_opens_it() {
        var opened: CalendarDay? = null
        runScreenTest(gym, screen = { calendar(onOpenVisit = { opened = it }) }) {
            onNodeWithTag("day-2023-11-10").performClick()
            waitForIdle()
            onNodeWithTag("add-visit").performScrollTo().assertTextEquals("Добавить визит")
            onNodeWithTag("add-visit").performClick()
            waitForIdle()
        }
        assertEquals(CalendarDay(2023, 11, 10), opened)
        assertEquals(
            listOf(sunday.id),
            gym.visits.rows.keys
                .toList(),
        )
    }

    @Test
    fun moving_onto_a_visit_replaces_it_only_once_confirmed() {
        val fifth = Visit(VisitId.random(), null, CalendarDay(2023, 11, 5), t0 - 9.days, t0, false)
        runBlocking { gym.visits.upsert(fifth) }
        runScreenTest(gym, screen = { calendar() }) {
            onNodeWithTag("day-2023-11-12").performClick()
            waitForIdle()
            onNodeWithTag("visit-menu-${sunday.id.value}").performScrollTo().performClick()
            onNodeWithTag("move-visit-${sunday.id.value}").performClick()
            waitForIdle()
            onNodeWithTag("day-2023-11-05").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("cancel-replace").performClick()
            waitForIdle()
            onNodeWithTag("move-banner").assertIsDisplayed()

            onNodeWithTag("day-2023-11-05").performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("confirm-replace").performClick()
            waitForIdle()

            onNodeWithTag("move-banner").assertDoesNotExist()
        }
        assertEquals(
            true,
            gym.visits.rows
                .getValue(fifth.id)
                .deleted,
        )
        assertEquals(
            CalendarDay(2023, 11, 5),
            gym.visits.rows
                .getValue(sunday.id)
                .day,
        )
    }

    @Test
    fun today_without_a_visit_offers_to_add_one() {
        var opened: CalendarDay? = null
        runScreenTest(gym, screen = { calendar(onOpenVisit = { opened = it }) }) {
            onNodeWithTag("calendar-empty").performScrollTo().assertTextEquals("Нет визита")
            onNodeWithTag("add-visit").performScrollTo().performClick()
            waitForIdle()
        }
        assertEquals(CalendarDay(2023, 11, 14), opened)
    }
}
