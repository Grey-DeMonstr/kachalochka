package monster.greyde.kachalochka.ui.calendar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга верхнего блока", null, t0)
    private val twelfth = CalendarDay(2023, 11, 12)
    private val sunday = Visit(VisitId.random(), null, twelfth, t0 - 2.days, t0, false)
    private val fifth = CalendarDay(2023, 11, 5)
    private val fifthVisit = Visit(VisitId.random(), null, fifth, t0 - 9.days, t0, false)

    private fun set(
        visit: Visit,
        machine: Machine,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        visit.userId,
        visit.id,
        machine.id,
        70.0,
        10,
        0,
        visit.recordedAt + minutes.minutes,
        t0,
        false,
    )

    private fun viewModel(gym: FakeGym = this.gym) =
        CalendarViewModel(
            gym.visits,
            gym.sets,
            gym.machines,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.utcOffset,
            gym.sync,
            gym.friends,
            FriendColorStore(gym.profiles, gym.clock, Random(1)),
        )

    private fun FakeGym.friendVisit(
        friend: Friend,
        day: CalendarDay?,
        recordedAt: Instant,
        vararg machines: Machine,
    ): Visit {
        val visit = Visit(VisitId.random(), friend.userId, day, recordedAt, t0, false)
        friends.visits += visit
        friends.sets += machines.mapIndexed { i, machine -> set(visit, machine, i) }
        return visit
    }

    private fun FakeGym.olegColored(index: Int) =
        runBlocking {
            profiles.upsert(
                Profile.new(ME.userId, t0).copy(friendColors = mapOf(OLEG.userId to index)),
            )
        }

    private fun CalendarUiState.day(n: Int): DayUi =
        weeks.flatten().filterNotNull().single { it.day.day == n }

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            gym.machines.upsert(press)
            gym.machines.upsert(row)
            gym.visits.upsert(sunday)
            gym.sets.upsert(set(sunday, press, 5))
            gym.sets.upsert(set(sunday, row, 10))
            gym.sets.upsert(set(sunday, row, 15))
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun it_opens_on_today_with_the_visit_days_marked() {
        val state = assertNotNull(viewModel().also { it.refresh() }.state.value)

        assertEquals("Ноябрь 2023", state.monthTitle)
        assertFalse(state.canShowNextMonth)
        assertEquals(listOf(null, null, 1, 2, 3, 4, 5), state.weeks.first().map { it?.day?.day })
        assertTrue(state.day(12).hasVisit)
        assertFalse(state.day(13).hasVisit)
        assertTrue(state.day(14).today && state.day(14).selected)
        assertFalse(state.day(15).enabled)
        assertEquals("Вторник, 14 ноября", state.dayTitle)
        assertNull(state.visit)
        assertTrue(state.noVisit)
        assertEquals(CalendarDay(2023, 11, 14), state.day)
    }

    @Test
    fun a_visit_no_client_has_dated_shows_on_the_day_it_was_recorded_on() =
        runTest {
            val undated = Visit(VisitId.random(), null, null, t0 - 3.days, t0, false)
            gym.visits.upsert(undated)
            gym.sets.upsert(set(undated, press, 5))
            val vm = viewModel().also { it.refresh() }

            vm.selectDay(CalendarDay(2023, 11, 11))

            val state = assertNotNull(vm.state.value)
            assertTrue(state.day(11).hasVisit)
            assertEquals(undated.id, state.visit?.id)
            assertEquals("1 тренажёр · 1 подход", state.visit?.counts)
        }

    @Test
    fun a_chosen_day_shows_its_visit_with_machines_and_sets() {
        val vm = viewModel().also { it.refresh() }

        vm.selectDay(twelfth)

        val state = assertNotNull(vm.state.value)
        assertEquals("Воскресенье, 12 ноября", state.dayTitle)
        val listed = assertNotNull(state.visit)
        assertEquals(sunday.id, listed.id)
        assertEquals("2 тренажёра · 3 подхода", listed.counts)
        assertEquals("Жим ногами, Тяга верхнего блока", listed.machines)
    }

    @Test
    fun a_chosen_day_shows_at_once_and_its_visits_follow_their_sets() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            val visitsRead = CompletableDeferred<Unit>()
            val setsRead = CompletableDeferred<Unit>()
            gym.visits.gate = visitsRead
            gym.sets.readGate = setsRead

            vm.selectDay(twelfth)

            val chosen = assertNotNull(vm.state.value)
            assertEquals("Воскресенье, 12 ноября", chosen.dayTitle)
            assertTrue(chosen.day(12).selected)
            assertNull(chosen.visit)
            assertFalse(chosen.noVisit)

            setsRead.complete(Unit)

            assertEquals(
                sunday.id,
                vm.state.value
                    ?.visit
                    ?.id,
            )
            visitsRead.complete(Unit)
        }

    @Test
    fun moving_a_visit_takes_its_sets_to_the_tapped_day() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            vm.selectDay(twelfth)

            vm.startMove(sunday.id)
            assertEquals(true, vm.state.value?.moving)
            vm.selectDay(CalendarDay(2023, 11, 5))

            val moved = assertNotNull(gym.visits.byId(sunday.id))
            assertEquals(5, CalendarDay.of(moved.recordedAt, Duration.ZERO).day)
            assertEquals(CalendarDay(2023, 11, 5), moved.day)
            assertEquals(
                listOf(5, 5, 5),
                gym.sets
                    .forVisit(sunday.id)
                    .map { CalendarDay.of(it.recordedAt, Duration.ZERO).day },
            )
            val state = assertNotNull(vm.state.value)
            assertFalse(state.moving)
            assertTrue(state.day(5).selected && state.day(5).hasVisit)
            assertFalse(state.day(12).hasVisit)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun cancelling_a_move_writes_nothing() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            vm.startMove(sunday.id)

            assertTrue(vm.cancelMove())
            assertFalse(vm.cancelMove())
            vm.selectDay(CalendarDay(2023, 11, 5))

            assertEquals(sunday, gym.visits.byId(sunday.id))
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun removing_the_visit_being_moved_leaves_move_mode_for_good() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            vm.selectDay(twelfth)
            vm.startMove(sunday.id)

            vm.askToRemove(sunday.id)
            vm.confirmRemoval()
            assertFalse(assertNotNull(vm.state.value).moving)
            vm.selectDay(CalendarDay(2023, 11, 5))

            assertEquals(true, gym.visits.byId(sunday.id)?.deleted)
            assertEquals(emptyList(), gym.visits.all(null))
            assertEquals(emptyList(), gym.sets.forVisit(sunday.id))
        }

    @Test
    fun a_visit_removed_elsewhere_during_a_move_is_not_brought_back() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            vm.startMove(sunday.id)
            val removed = sunday.copy(updatedAt = t0 + 1.minutes, deleted = true)
            gym.visits.upsert(removed)

            vm.selectDay(CalendarDay(2023, 11, 5))

            assertEquals(removed, gym.visits.byId(sunday.id))
            assertEquals(
                listOf(12, 12, 12),
                gym.sets
                    .forVisit(sunday.id)
                    .map { CalendarDay.of(it.recordedAt, Duration.ZERO).day },
            )
            assertFalse(assertNotNull(vm.state.value).moving)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun removal_asks_first_and_then_takes_the_sets_with_it() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            vm.selectDay(twelfth)

            vm.askToRemove(sunday.id)
            val removal = assertNotNull(vm.state.value?.removal)
            assertEquals("Удалить визит?", removal.title)
            assertEquals(
                "12 ноября · 3 подхода. Подходы пропадут из истории и статистики.",
                removal.text,
            )
            vm.cancelRemoval()
            assertNull(vm.state.value?.removal)
            assertEquals(false, gym.visits.byId(sunday.id)?.deleted)

            vm.askToRemove(sunday.id)
            vm.confirmRemoval()

            assertEquals(true, gym.visits.byId(sunday.id)?.deleted)
            assertEquals(emptyList(), gym.sets.forVisit(sunday.id))
            assertEquals(
                3,
                gym.sets.rows.values
                    .count { it.deleted },
            )
            val state = assertNotNull(vm.state.value)
            assertNull(state.removal)
            assertFalse(state.day(12).hasVisit)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun the_next_month_is_out_of_reach_and_the_previous_one_is_not() {
        val vm = viewModel().also { it.refresh() }

        vm.showMonth(+1)
        assertEquals("Ноябрь 2023", vm.state.value?.monthTitle)
        vm.showMonth(-1)

        val state = assertNotNull(vm.state.value)
        assertEquals("Октябрь 2023", state.monthTitle)
        assertTrue(state.canShowNextMonth)
    }

    @Test
    fun switching_accounts_shows_the_other_account_s_visits() =
        runTest {
            val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
            val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
            val two = FakeGym().withAccounts(ivan, misha, active = ivan)
            two.visits.upsert(sunday.copy(userId = ivan.account.userId))
            val vm = viewModel(two).also { it.selectDay(twelfth) }
            assertEquals(
                sunday.id,
                vm.state.value
                    ?.visit
                    ?.id,
            )

            two.accounts.switchTo(misha.account.userId)

            val state = assertNotNull(vm.state.value)
            assertNull(state.visit)
            assertFalse(state.day(12).hasVisit)
        }

    @Test
    fun switching_accounts_leaves_move_mode() =
        runTest {
            val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
            val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
            val two = FakeGym().withAccounts(ivan, misha, active = ivan)
            val ivanVisit = sunday.copy(userId = ivan.account.userId)
            two.visits.upsert(ivanVisit)
            val vm = viewModel(two)
            vm.startMove(ivanVisit.id)
            assertEquals(true, vm.state.value?.moving)

            two.accounts.switchTo(misha.account.userId)

            assertFalse(assertNotNull(vm.state.value).moving)
        }

    @Test
    fun a_reload_overtaken_by_an_account_switch_never_lands() =
        runTest {
            val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
            val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
            val two = FakeGym().withAccounts(ivan, misha, active = ivan)
            two.visits.upsert(sunday.copy(userId = ivan.account.userId))
            val vm = viewModel(two)
            val gate = CompletableDeferred<Unit>()
            two.visits.gate = gate
            vm.refresh()
            two.visits.gate = null

            two.accounts.switchTo(misha.account.userId)
            gate.complete(Unit)

            assertFalse(assertNotNull(vm.state.value).day(12).hasVisit)
        }

    @Test
    fun a_completed_sync_reloads_the_calendar() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            gym.visits.upsert(Visit(VisitId.random(), null, gym.today, t0, t0, false))

            gym.sync.completePass()

            assertTrue(
                vm.state.value
                    ?.day(14)
                    ?.hasVisit == true,
            )
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_day_s_card_shows_the_visit_with_sets_over_a_later_empty_one() =
        runTest {
            val laterEmpty =
                Visit(VisitId.random(), null, twelfth, t0 - 2.days + 1.hours, t0, false)
            gym.visits.upsert(laterEmpty)
            val vm = viewModel().also { it.selectDay(twelfth) }

            assertEquals(
                sunday.id,
                vm.state.value
                    ?.visit
                    ?.id,
            )
        }

    @Test
    fun a_day_shows_the_newest_with_sets_of_its_visits_those_not_yet_given_a_day_included() =
        runTest {
            val later = Visit(VisitId.random(), null, twelfth, t0 - 2.days + 1.hours, t0, false)
            gym.visits.upsert(later)
            gym.sets.upsert(set(later, press, 20))
            val vm = viewModel().also { it.selectDay(twelfth) }
            assertEquals(
                later.id,
                vm.state.value
                    ?.visit
                    ?.id,
            )

            val latest = Visit(VisitId.random(), null, null, t0 - 2.days + 90.minutes, t0, false)
            gym.visits.upsert(latest)
            gym.sets.upsert(set(latest, press, 25))
            vm.refresh()

            assertEquals(
                latest.id,
                vm.state.value
                    ?.visit
                    ?.id,
            )
        }

    @Test
    fun tapping_the_moving_visit_s_own_day_leaves_move_mode_without_writing() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            vm.startMove(sunday.id)

            vm.selectDay(twelfth)

            assertFalse(assertNotNull(vm.state.value).moving)
            assertEquals(sunday, gym.visits.byId(sunday.id))
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun moving_onto_a_day_with_a_visit_asks_before_replacing_it() =
        runTest {
            gym.visits.upsert(fifthVisit)
            gym.sets.upsert(set(fifthVisit, press, 0))
            val vm = viewModel().also { it.refresh() }
            vm.startMove(sunday.id)

            vm.selectDay(fifth)

            val replacement = assertNotNull(vm.state.value?.replacement)
            assertEquals("Заменить визит?", replacement.title)
            assertEquals(
                "На 5 ноября уже есть визит: 1 подход. " +
                    "Он и его подходы пропадут из истории и статистики.",
                replacement.text,
            )
            vm.cancelReplacement()
            val state = assertNotNull(vm.state.value)
            assertNull(state.replacement)
            assertTrue(state.moving)
            assertEquals(fifthVisit, gym.visits.byId(fifthVisit.id))
            assertEquals(twelfth, gym.visits.byId(sunday.id)?.day)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun replacing_removes_the_day_s_visit_with_its_sets_and_moves_in() =
        runTest {
            gym.visits.upsert(fifthVisit)
            gym.sets.upsert(set(fifthVisit, press, 0))
            val vm = viewModel().also { it.refresh() }
            vm.startMove(sunday.id)
            vm.selectDay(fifth)

            vm.confirmReplacement()

            assertEquals(true, gym.visits.byId(fifthVisit.id)?.deleted)
            assertEquals(emptyList(), gym.sets.forVisit(fifthVisit.id))
            assertEquals(fifth, gym.visits.byId(sunday.id)?.day)
            assertEquals(3, gym.sets.forVisit(sunday.id).size)
            val state = assertNotNull(vm.state.value)
            assertFalse(state.moving)
            assertNull(state.replacement)
            assertTrue(state.day(5).selected)
            assertEquals(sunday.id, state.visit?.id)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun replacing_removes_every_visit_on_the_day_normalization_has_not_reached() =
        runTest {
            gym.visits.upsert(fifthVisit)
            gym.sets.upsert(set(fifthVisit, press, 0))
            val undated = Visit(VisitId.random(), null, null, t0 - 9.days + 1.hours, t0, false)
            gym.visits.upsert(undated)
            gym.sets.upsert(set(undated, row, 0))
            val vm = viewModel().also { it.refresh() }
            vm.startMove(sunday.id)
            vm.selectDay(fifth)

            vm.confirmReplacement()

            assertEquals(true, gym.visits.byId(fifthVisit.id)?.deleted)
            assertEquals(true, gym.visits.byId(undated.id)?.deleted)
            assertEquals(emptyList(), gym.sets.forVisit(fifthVisit.id))
            assertEquals(emptyList(), gym.sets.forVisit(undated.id))
            assertEquals(listOf(sunday.id), gym.visits.all(null).map { it.id })
            assertEquals(fifth, gym.visits.byId(sunday.id)?.day)
        }

    @Test
    fun a_day_that_gained_a_visit_since_the_calendar_loaded_asks_before_the_move() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            vm.startMove(sunday.id)
            // A sync pulls a visit onto the day before the calendar reloads.
            gym.visits.upsert(fifthVisit)
            gym.sets.upsert(set(fifthVisit, press, 0))

            vm.selectDay(fifth)

            val state = assertNotNull(vm.state.value)
            assertEquals("Заменить визит?", state.replacement?.title)
            assertTrue(state.moving)
            assertEquals(twelfth, gym.visits.byId(sunday.id)?.day)
            assertEquals(fifthVisit, gym.visits.byId(fifthVisit.id))
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun confirming_replaces_whoever_occupies_the_day_by_confirm_time() =
        runTest {
            gym.visits.upsert(fifthVisit)
            gym.sets.upsert(set(fifthVisit, press, 0))
            val vm = viewModel().also { it.refresh() }
            vm.startMove(sunday.id)
            vm.selectDay(fifth)

            // A sync pulls a different visit onto the day between the ask and the confirmation.
            gym.visits.upsert(fifthVisit.copy(updatedAt = t0 + 1.minutes, deleted = true))
            val pulled = Visit(VisitId.random(), null, fifth, t0 - 1.days, t0, false)
            gym.visits.upsert(pulled)
            gym.sets.upsert(set(pulled, press, 0))

            vm.confirmReplacement()

            assertEquals(true, gym.visits.byId(pulled.id)?.deleted)
            assertEquals(emptyList(), gym.sets.forVisit(pulled.id))
            assertEquals(fifth, gym.visits.byId(sunday.id)?.day)
            assertEquals(3, gym.sets.forVisit(sunday.id).size)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun confirming_after_the_day_is_vacated_still_moves_and_deletes_nothing_else() =
        runTest {
            gym.visits.upsert(fifthVisit)
            gym.sets.upsert(set(fifthVisit, press, 0))
            val vm = viewModel().also { it.refresh() }
            vm.startMove(sunday.id)
            vm.selectDay(fifth)

            // A sync removes the occupant before the confirmation, leaving the day free.
            gym.visits.upsert(fifthVisit.copy(updatedAt = t0 + 1.minutes, deleted = true))

            vm.confirmReplacement()

            assertEquals(fifth, gym.visits.byId(sunday.id)?.day)
            assertEquals(3, gym.sets.forVisit(sunday.id).size)
            assertEquals(
                0,
                gym.sets.rows.values
                    .count { it.deleted },
            )
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_friend_s_visit_on_the_chosen_day_shows_in_their_colour_with_its_counts() =
        runTest {
            val signed = signedInGym()
            signed.friends.group("Зал на Лесной", owner = OLEG, ME)
            signed.olegColored(5)
            signed.friendVisit(OLEG, twelfth, t0 - 2.days, press, press, row)

            val vm = viewModel(signed).also { it.selectDay(twelfth) }

            val state = assertNotNull(vm.state.value)
            assertEquals(listOf(5), state.day(12).friendDots)
            assertEquals(
                listOf(
                    FriendDayVisitUi(OLEG.userId, "Олег", 5, twelfth, "2 тренажёра · 3 подхода"),
                ),
                state.friendVisits,
            )
        }

    @Test
    fun a_friend_s_counts_follow_once_their_sets_are_read() =
        runTest {
            val signed = signedInGym()
            signed.friends.group("Зал на Лесной", owner = OLEG, ME)
            signed.olegColored(5)
            signed.friendVisit(OLEG, twelfth, t0 - 2.days, press)
            val vm = viewModel(signed)
            val setsRead = CompletableDeferred<Unit>()
            signed.friends.gate = setsRead

            vm.selectDay(twelfth)

            assertEquals(
                listOf(FriendDayVisitUi(OLEG.userId, "Олег", 5, twelfth, null)),
                vm.state.value?.friendVisits,
            )
            setsRead.complete(Unit)
            assertEquals(
                "1 тренажёр · 1 подход",
                vm.state.value
                    ?.friendVisits
                    ?.single()
                    ?.counts,
            )
        }

    @Test
    fun each_friend_shows_once_a_day_by_name_those_without_a_day_placed_by_their_time() =
        runTest {
            val signed = signedInGym()
            signed.friends.group("Зал на Лесной", owner = PASHA, OLEG, ME)
            signed.profiles.upsert(Profile.new(ME.userId, t0))
            signed.friendVisit(PASHA, twelfth, t0 - 2.days, press)
            signed.friendVisit(OLEG, twelfth, t0 - 2.days, press)
            signed.friendVisit(OLEG, null, t0 - 2.days + 1.hours, row)

            val vm = viewModel(signed).also { it.selectDay(twelfth) }

            val state = assertNotNull(vm.state.value)
            val colors = assertNotNull(signed.profiles.forOwner(ME.userId)).friendColors
            assertEquals(
                listOf(colors.getValue(OLEG.userId), colors.getValue(PASHA.userId)),
                state.day(12).friendDots,
            )
            assertEquals(listOf("Олег", "Паша"), state.friendVisits.map { it.name })
        }

    @Test
    fun own_visits_show_while_friends_are_still_read() =
        runTest {
            val signed = signedInGym()
            signed.visits.upsert(sunday.copy(userId = ME.userId))
            signed.friends.group("Зал на Лесной", owner = OLEG, ME)
            signed.friendVisit(OLEG, twelfth, t0 - 2.days, press)
            val friendsRead = CompletableDeferred<Unit>()
            signed.friends.gate = friendsRead

            val vm = viewModel(signed).also { it.refresh() }

            val waiting = assertNotNull(vm.state.value).day(12)
            assertTrue(waiting.hasVisit)
            assertEquals(emptyList(), waiting.friendDots)
            friendsRead.complete(Unit)
            assertEquals(
                1,
                vm.state.value
                    ?.day(12)
                    ?.friendDots
                    ?.size,
            )
        }

    @Test
    fun offline_the_calendar_shows_no_friends_and_no_failure() =
        runTest {
            val signed = signedInGym()
            signed.visits.upsert(sunday.copy(userId = ME.userId))
            signed.friends.group("Зал на Лесной", owner = OLEG, ME)
            signed.friendVisit(OLEG, twelfth, t0 - 2.days, press)
            signed.friends.offline = true

            val vm = viewModel(signed).also { it.selectDay(twelfth) }

            val state = assertNotNull(vm.state.value)
            assertEquals(sunday.id, state.visit?.id)
            assertEquals(emptyList(), state.day(12).friendDots)
            assertEquals(emptyList(), state.friendVisits)
        }

    @Test
    fun switching_accounts_drops_the_previous_account_s_friends() =
        runTest {
            val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
            val two = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            two.friends.group("Зал на Лесной", owner = OLEG, ME)
            two.friendVisit(OLEG, twelfth, t0 - 2.days, press)
            val vm = viewModel(two).also { it.selectDay(twelfth) }
            assertEquals(
                1,
                vm.state.value
                    ?.friendVisits
                    ?.size,
            )
            val mishasFriends = CompletableDeferred<Unit>()
            two.friends.gate = mishasFriends

            two.accounts.switchTo(misha.account.userId)

            val state = assertNotNull(vm.state.value)
            assertEquals(emptyList(), state.day(12).friendDots)
            assertEquals(emptyList(), state.friendVisits)
            mishasFriends.complete(Unit)
            assertEquals(emptyList(), vm.state.value?.friendVisits)
        }

    @Test
    fun another_month_reads_friends_within_its_own_days() =
        runTest {
            val signed = signedInGym()
            signed.friends.group("Зал на Лесной", owner = OLEG, ME)
            signed.friendVisit(OLEG, CalendarDay(2023, 10, 20), t0 - 25.days, press)
            val vm = viewModel(signed).also { it.refresh() }
            assertEquals(
                CalendarDay(2023, 11, 1) to CalendarDay(2023, 11, 30),
                signed.friends.visitWindows.last(),
            )

            vm.showMonth(-1)

            assertEquals(
                CalendarDay(2023, 10, 1) to CalendarDay(2023, 10, 31),
                signed.friends.visitWindows.last(),
            )
            assertEquals(
                1,
                vm.state.value
                    ?.day(20)
                    ?.friendDots
                    ?.size,
            )
        }

    @Test
    fun entering_again_or_a_sync_reads_friends_again() =
        runTest {
            val signed = signedInGym()
            signed.friends.group("Зал на Лесной", owner = OLEG, ME)
            val vm = viewModel(signed).also { it.refresh() }
            assertEquals(
                emptyList(),
                vm.state.value
                    ?.day(13)
                    ?.friendDots,
            )

            signed.friendVisit(OLEG, CalendarDay(2023, 11, 13), t0 - 1.days, press)
            vm.refresh()
            assertEquals(
                1,
                vm.state.value
                    ?.day(13)
                    ?.friendDots
                    ?.size,
            )

            signed.friendVisit(OLEG, CalendarDay(2023, 11, 10), t0 - 4.days, press)
            signed.sync.completePass()
            assertEquals(
                1,
                vm.state.value
                    ?.day(10)
                    ?.friendDots
                    ?.size,
            )
        }

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)
}
