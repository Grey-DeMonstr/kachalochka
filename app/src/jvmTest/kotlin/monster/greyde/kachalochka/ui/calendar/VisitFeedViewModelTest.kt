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
import monster.greyde.kachalochka.core.domain.gym.DayBest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import monster.greyde.kachalochka.ui.strings.inEnglish
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
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class VisitFeedViewModelTest {
    private val gym = signedInGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", ME.userId, t0)
    private val today = CalendarDay(2023, 11, 14)

    private fun FakeGym.ownVisit(
        day: CalendarDay,
        weight: Double = 70.0,
    ): Visit {
        val recorded = day.at(10 * 3_600_000L, Duration.ZERO)
        val visit = Visit(VisitId.random(), ME.userId, day, recorded, t0, false)
        runBlocking {
            visits.upsert(visit)
            sets.upsert(set(visit, press, weight))
        }
        return visit
    }

    private fun set(
        visit: Visit,
        machine: Machine,
        weight: Double,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        visit.userId,
        visit.id,
        machine.id,
        weight,
        10,
        0,
        visit.recordedAt + 5.minutes,
        t0,
        false,
    )

    private fun FakeGym.friendVisit(
        friend: Friend,
        day: CalendarDay,
    ): Visit {
        val visit =
            Visit(VisitId.random(), friend.userId, day, day.at(0L, Duration.ZERO), t0, false)
        val theirs = press.copy(userId = friend.userId)
        friends.visits += visit
        friends.sets += set(visit, theirs, 60.0)
        if (friends.machines.none { it.id == theirs.id && it.userId == friend.userId }) {
            friends.machines += theirs
        }
        return visit
    }

    private val people = InMemoryFeedPeoplePreference()

    private fun viewModel(gym: FakeGym = this.gym) =
        VisitFeedViewModel(
            gym.visits,
            gym.sets,
            gym.machines,
            gym.currentUser,
            gym.accounts,
            gym.deviceClock,
            gym.placement,
            gym.sync,
            gym.friends,
            FriendColorStore(gym.profiles, gym.clock, gym.friends),
            AccountAvatars(gym.accounts, gym.profiles),
            gym.profiles,
            people,
            gym.priorBests,
        )

    private fun FeedUiState.cardsOn(day: CalendarDay): List<VisitCardUi> =
        days.single { it.day == day }.cards

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            gym.machines.upsert(press)
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun days_run_newest_first_today_and_yesterday_named_with_their_date_aside() =
        runTest {
            gym.ownVisit(today)
            gym.ownVisit(CalendarDay(2023, 11, 12))
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, CalendarDay(2023, 11, 13))

            val state = assertNotNull(viewModel().state.value)

            assertEquals(
                listOf(today, CalendarDay(2023, 11, 13), CalendarDay(2023, 11, 12)),
                state.days.map { it.day },
            )
            assertEquals(
                listOf("Сегодня", "Вчера", "Воскресенье, 12 ноября"),
                state.days.map { it.title },
            )
            assertEquals(
                listOf("вторник, 14 ноября", "понедельник, 13 ноября", null),
                state.days.map { it.aside },
            )
            assertEquals(listOf("Ноябрь 2023", null, null), state.days.map { it.month })
            assertFalse(state.loading)
        }

    @Test
    fun a_day_shows_the_own_card_first_then_friends_by_name() =
        runTest {
            val mine = gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = PASHA, OLEG, ME)
            gym.friendVisit(PASHA, today)
            gym.friendVisit(OLEG, today)

            val cards = assertNotNull(viewModel().state.value).cardsOn(today)

            assertEquals(listOf("Иван", "Олег", "Паша"), cards.map { it.person.name })
            assertEquals(mine.id, cards.first().own?.id)
            assertEquals(
                listOf(ExerciseUi(press.name, "60 кг × 10", null)),
                cards.last().exercises,
            )
        }

    @Test
    fun the_own_card_shows_the_gain_its_sets_carry() =
        runTest {
            val latest = gym.ownVisit(today, weight = 65.0)
            gym.sets.forVisit(latest.id).forEach {
                gym.sets.upsert(it.copy(priorBest = DayBest(60.0, 10, 1)))
            }

            val card = assertNotNull(viewModel().state.value).cardsOn(today).single()

            assertEquals("+5 кг", card.exercises?.single()?.growth)
        }

    @Test
    fun removing_a_visit_in_the_feed_clears_the_later_day_s_prior_best() =
        runTest {
            val first = gym.ownVisit(CalendarDay(2023, 11, 1), weight = 60.0)
            val latest = gym.ownVisit(today, weight = 65.0)
            gym.priorBests.refreshAll(ME.userId)
            assertEquals(
                DayBest(60.0, 10, 1),
                gym.sets
                    .forVisit(latest.id)
                    .single()
                    .priorBest,
            )
            val vm = viewModel()

            vm.askToRemove(first.id)
            vm.confirmRemoval()

            assertEquals(
                DayBest.NONE,
                gym.sets
                    .forVisit(latest.id)
                    .single()
                    .priorBest,
            )
        }

    @Test
    fun three_weeks_show_first_and_each_load_reads_three_more() =
        runTest {
            gym.ownVisit(today)
            gym.ownVisit(CalendarDay(2023, 10, 20))
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, CalendarDay(2023, 9, 1))
            val vm = viewModel()

            assertEquals(
                listOf(today),
                vm.state.value
                    ?.days
                    ?.map { it.day },
            )
            assertEquals(
                CalendarDay(2023, 10, 25) to today,
                gym.friends.visitWindows.last(),
            )

            vm.loadMore()

            val state = assertNotNull(vm.state.value)
            assertEquals(listOf(today, CalendarDay(2023, 10, 20)), state.days.map { it.day })
            assertEquals("Октябрь 2023", state.days.last().month)
            assertEquals(
                CalendarDay(2023, 10, 4) to CalendarDay(2023, 10, 24),
                gym.friends.visitWindows.last(),
            )
            assertFalse(state.ended)
        }

    @Test
    fun weeks_without_visits_are_read_through_to_the_next_visit() =
        runTest {
            gym.ownVisit(today)
            gym.ownVisit(CalendarDay(2023, 6, 1))
            val vm = viewModel()

            vm.loadMore()

            val state = assertNotNull(vm.state.value)
            assertEquals(listOf(today, CalendarDay(2023, 6, 1)), state.days.map { it.day })
            assertTrue(state.ended)
        }

    @Test
    fun the_feed_ends_at_the_oldest_visit_of_the_account_and_its_friends() =
        runTest {
            gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, CalendarDay(2023, 10, 10))
            val withFriends = viewModel()
            assertFalse(assertNotNull(withFriends.state.value).ended)

            withFriends.loadMore()

            assertTrue(assertNotNull(withFriends.state.value).ended)
            val requests = gym.friends.visitWindows.size
            withFriends.loadMore()
            assertEquals(requests, gym.friends.visitWindows.size)
        }

    @Test
    fun the_oldest_own_visit_ends_the_feed_without_friends() =
        runTest {
            gym.ownVisit(CalendarDay(2023, 11, 10))

            assertTrue(assertNotNull(viewModel().state.value).ended)
        }

    @Test
    fun without_any_visit_the_feed_is_empty_and_ended() {
        val state = assertNotNull(viewModel().state.value)

        assertEquals(emptyList(), state.days)
        assertTrue(state.ended)
    }

    @Test
    fun offline_the_feed_shows_the_own_visits_only() =
        runTest {
            gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, today)
            gym.friendVisit(OLEG, CalendarDay(2023, 1, 1))
            gym.friends.offline = true

            val state = assertNotNull(viewModel().state.value)

            assertEquals(listOf("Иван"), state.cardsOn(today).map { it.person.name })
            assertTrue(state.ended)
        }

    @Test
    fun own_days_show_while_friends_are_read() =
        runTest {
            gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, today)
            val read = CompletableDeferred<Unit>()
            gym.friends.gate = read

            val vm = viewModel()

            val waiting = assertNotNull(vm.state.value)
            assertEquals(1, waiting.cardsOn(today).size)
            assertTrue(waiting.loading)
            read.complete(Unit)
            assertEquals(
                2,
                vm.state.value
                    ?.cardsOn(today)
                    ?.size,
            )
        }

    @Test
    fun removal_asks_first_then_takes_the_visit_and_its_sets_away() =
        runTest {
            val visit = gym.ownVisit(CalendarDay(2023, 11, 12))
            val vm = viewModel()

            vm.askToRemove(visit.id)
            assertEquals(
                "Воскресенье, 12 ноября",
                vm.state.value
                    ?.removal
                    ?.text,
            )
            vm.confirmRemoval()

            val state = assertNotNull(vm.state.value)
            assertNull(state.removal)
            assertEquals(emptyList(), state.days)
            assertEquals(true, gym.visits.byId(visit.id)?.deleted)
            assertEquals(emptyList(), gym.sets.forVisit(visit.id))
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun repeating_a_visit_today_opens_today() =
        runTest {
            gym.ownVisit(CalendarDay(2023, 11, 12))
            var opened: CalendarDay? = null

            viewModel().repeatToday(CalendarDay(2023, 11, 12)) { opened = it }

            assertEquals(today, opened)
        }

    @Test
    fun switching_accounts_shows_the_other_account_s_feed() =
        runTest {
            gym.ownVisit(today)
            val misha =
                AccountSession(
                    Account(
                        UserId("22222222-2222-4222-8222-222222222222"),
                        "m@example.test",
                        "Миша",
                    ),
                    "access",
                    "refresh",
                    t0,
                )
            val two = gym.withAccounts(misha, active = IVAN_SESSION)
            val vm = viewModel(two)
            assertEquals(
                1,
                vm.state.value
                    ?.days
                    ?.size,
            )

            two.accounts.switchTo(misha.account.userId)

            assertEquals(emptyList(), vm.state.value?.days)
        }

    @Test
    fun the_people_are_the_account_then_its_friends_by_name_none_selected_at_first() =
        runTest {
            gym.friends.group("Зал на Лесной", owner = PASHA, OLEG, ME)

            val shown = assertNotNull(viewModel().state.value).people

            assertEquals(listOf("Иван", "Олег", "Паша"), shown.map { it.person.name })
            assertEquals(listOf(false, false, false), shown.map { it.selected })
            assertNull(shown.first().color)
            assertNotNull(shown.last().color)
        }

    @Test
    fun a_signed_out_feed_has_no_people() {
        val gym = FakeGym()
        runBlocking { gym.machines.upsert(press) }

        assertEquals(emptyList(), assertNotNull(viewModel(gym).state.value).people)
    }

    @Test
    fun a_selected_friend_is_shown_alone_and_the_device_keeps_it_for_the_account() =
        runTest {
            gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, today)
            val vm = viewModel()

            vm.select(OLEG.userId)

            val state = assertNotNull(vm.state.value)
            assertEquals(listOf("Олег"), state.cardsOn(today).map { it.person.name })
            assertEquals(listOf(false, true), state.people.map { it.selected })
            assertEquals(setOf(SelectedPerson(ME.userId, OLEG.userId)), people.selected.value)
            assertEquals(
                listOf("Олег"),
                assertNotNull(viewModel().state.value).cardsOn(today).map { it.person.name },
            )
        }

    @Test
    fun selecting_the_selected_person_again_shows_everyone() =
        runTest {
            gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, today)
            val vm = viewModel()

            vm.select(OLEG.userId)
            vm.select(OLEG.userId)

            assertEquals(2, assertNotNull(vm.state.value).cardsOn(today).size)
            assertEquals(emptySet(), people.selected.value)
        }

    @Test
    fun selecting_another_person_moves_the_selection() =
        runTest {
            gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, today)
            val vm = viewModel()

            vm.select(OLEG.userId)
            vm.select(ME.userId)

            val state = assertNotNull(vm.state.value)
            assertEquals(listOf("Иван"), state.cardsOn(today).map { it.person.name })
            assertEquals(setOf(SelectedPerson(ME.userId, ME.userId)), people.selected.value)
        }

    @Test
    fun another_account_s_selection_leaves_everyone_shown() =
        runTest {
            gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, today)
            people.set(setOf(SelectedPerson(PASHA.userId, OLEG.userId)))

            assertEquals(2, assertNotNull(viewModel().state.value).cardsOn(today).size)
        }

    @Test
    fun paging_reads_on_to_a_visit_of_the_selected_person() =
        runTest {
            gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, CalendarDay(2023, 10, 1))
            people.set(setOf(SelectedPerson(ME.userId, OLEG.userId)))

            val state = assertNotNull(viewModel().state.value)

            assertEquals(listOf(CalendarDay(2023, 10, 1)), state.days.map { it.day })
            assertFalse(state.loading)
        }

    @Test
    fun selecting_a_person_without_visits_read_reads_on_to_their_visit() =
        runTest {
            gym.ownVisit(today)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friendVisit(OLEG, CalendarDay(2023, 10, 1))
            val vm = viewModel()

            vm.select(OLEG.userId)

            val state = assertNotNull(vm.state.value)
            assertEquals(listOf(CalendarDay(2023, 10, 1)), state.days.map { it.day })
        }

    @Test
    fun the_feed_speaks_english() =
        runTest {
            gym.ownVisit(today)
            gym.ownVisit(CalendarDay(2023, 11, 13))

            inEnglish {
                val state = assertNotNull(viewModel().state.value)
                assertEquals(listOf("Today", "Yesterday"), state.days.map { it.title })
                assertEquals("Tuesday, 14 November", state.days.first().aside)
            }
        }
}
