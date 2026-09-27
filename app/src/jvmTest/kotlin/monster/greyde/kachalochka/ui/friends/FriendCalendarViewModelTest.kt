package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class FriendCalendarViewModelTest {
    private val gym = signedInGym()
    private val fixture = OlegVisitFixture(gym)
    private val t0 = gym.clock.current
    private val undated = Visit(VisitId.random(), OLEG.userId, null, t0 - 3.days, t0, false)

    private fun viewModel() =
        FriendCalendarViewModel(
            OLEG.userId,
            gym.friends,
            gym.machines,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.utcOffset,
        )

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            fixture.install()
            gym.friends.visits += undated
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun it_opens_on_today_with_the_friend_s_visit_days_marked() {
        val state = assertNotNull(viewModel().state.value)

        val marked =
            state.weeks
                .flatten()
                .filterNotNull()
                .filter { it.hasVisit }
                .map { it.day }
        assertEquals(listOf(CalendarDay(2023, 11, 13)), marked)
        assertEquals(gym.today, state.day)
        assertNull(state.visit)
        assertTrue(state.noVisit)
    }

    @Test
    fun a_chosen_day_shows_their_visit_under_the_viewer_s_machine_names() {
        val vm = viewModel()

        vm.selectDay(CalendarDay(2023, 11, 13))

        assertEquals(
            FriendDayUi("2 тренажёра · 3 подхода", "Жим ногами, Тяга"),
            vm.state.value?.visit,
        )
    }

    @Test
    fun a_future_day_cannot_be_chosen() {
        val vm = viewModel()

        vm.selectDay(gym.today.plusDays(1))

        assertEquals(gym.today, vm.state.value?.day)
    }

    @Test
    fun offline_the_calendar_says_so_and_a_retry_reads_it_again() {
        gym.friends.offline = true
        val vm = viewModel()
        assertTrue(vm.offline.value)

        gym.friends.offline = false
        vm.refresh()

        assertFalse(vm.offline.value)
    }
}
