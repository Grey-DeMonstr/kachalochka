package monster.greyde.kachalochka.ui.calendar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
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
    private val sunday =
        Visit(VisitId.random(), null, t0 - 2.days, t0 - 2.days + 1.hours, t0, false)
    private val twelfth = CalendarDay(2023, 11, 12)

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
        )

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
        assertEquals(emptyList(), state.visits)
        assertEquals("Начать визит", state.addLabel)
    }

    @Test
    fun a_chosen_day_lists_its_visits_with_machines_and_sets() {
        val vm = viewModel().also { it.refresh() }

        vm.selectDay(twelfth)

        val state = assertNotNull(vm.state.value)
        assertEquals("Воскресенье, 12 ноября", state.dayTitle)
        val listed = state.visits.single()
        assertEquals(sunday.id, listed.id)
        assertEquals("2 тренажёра · 3 подхода", listed.counts)
        assertEquals("Жим ногами, Тяга верхнего блока", listed.machines)
        assertFalse(listed.running)
        assertEquals("Добавить визит", state.addLabel)
    }

    @Test
    fun adding_on_a_past_day_records_an_ended_visit_and_opens_it() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            vm.selectDay(CalendarDay(2023, 11, 10))
            var opened: VisitId? = null

            vm.addVisit { opened = it }

            val added = assertNotNull(gym.visits.byId(assertNotNull(opened)))
            assertEquals(Instant.parse("2023-11-10T12:00:00Z"), added.recordedAt)
            assertEquals(added.recordedAt, added.endedAt)
            assertNull(gym.visits.active(null))
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun adding_on_today_starts_the_running_visit() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            var opened: VisitId? = null

            vm.addVisit { opened = it }

            val running = assertNotNull(gym.visits.active(null))
            assertEquals(running.id, opened)
            assertEquals(t0, running.recordedAt)
        }

    @Test
    fun adding_on_today_opens_the_visit_already_running() =
        runTest {
            val running = Visit(VisitId.random(), null, t0 - 1.hours, null, t0, false)
            gym.visits.upsert(running)
            val vm = viewModel().also { it.refresh() }
            var opened: VisitId? = null

            vm.addVisit { opened = it }

            assertEquals(running.id, opened)
            assertEquals(
                1,
                gym.visits.rows.values
                    .count { it.endedAt == null && !it.deleted },
            )
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun today_offers_no_second_running_visit() =
        runTest {
            gym.visits.upsert(Visit(VisitId.random(), null, t0 - 1.hours, null, t0, false))

            val state = assertNotNull(viewModel().also { it.refresh() }.state.value)

            assertNull(state.addLabel)
            assertTrue(state.visits.single().running)
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
    fun a_visit_that_has_not_ended_cannot_be_moved() =
        runTest {
            val running = Visit(VisitId.random(), null, t0 - 1.hours, null, t0, false)
            gym.visits.upsert(running)
            val vm = viewModel().also { it.refresh() }

            vm.startMove(running.id)

            assertEquals(false, vm.state.value?.moving)
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
                1,
                vm.state.value
                    ?.visits
                    ?.size,
            )

            two.accounts.switchTo(misha.account.userId)

            val state = assertNotNull(vm.state.value)
            assertEquals(emptyList(), state.visits)
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
            gym.visits.upsert(Visit(VisitId.random(), null, t0, t0, t0, false))

            gym.sync.completePass()

            assertTrue(
                vm.state.value
                    ?.day(14)
                    ?.hasVisit == true,
            )
            assertEquals(0, gym.sync.requests)
        }

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)
}
