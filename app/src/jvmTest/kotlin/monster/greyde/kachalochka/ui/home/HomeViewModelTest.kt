package monster.greyde.kachalochka.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.strings.inEnglish
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        HomeViewModel(gym.visits, gym.sets, gym.currentUser, gym.clock, gym.utcOffset, gym.sync)

    private suspend fun setOn(
        visit: Visit,
        at: Instant = t0,
    ) {
        val press = Machine.new("Жим ногами", null, t0)
        gym.visits.upsert(visit)
        gym.machines.upsert(press)
        gym.sets.upsert(
            WorkoutSet(
                WorkoutSetId.random(),
                null,
                visit.id,
                press.id,
                70.0,
                10,
                0,
                at,
                t0,
                false,
            ),
        )
    }

    @Test
    fun a_day_without_a_visit_offers_to_start_one() {
        val vm = viewModel().also { it.refresh() }

        assertEquals(
            HomeUiState(TodayUi(gym.today, "Вторник, 14 ноября 2023", started = false)),
            vm.state.value,
        )
    }

    @Test
    fun a_day_with_a_set_offers_to_continue() =
        runTest {
            setOn(Visit(VisitId.random(), null, gym.today, t0, t0, false))

            val vm = viewModel().also { it.refresh() }

            assertEquals(
                true,
                vm.state.value
                    ?.today
                    ?.started,
            )
        }

    @Test
    fun a_started_plan_counts_as_a_visit() =
        runTest {
            val press = Machine.new("Жим ногами", null, t0)
            gym.machines.upsert(press)
            gym.visits.upsert(
                Visit(
                    VisitId.random(),
                    null,
                    gym.today,
                    t0,
                    t0,
                    false,
                    planned = listOf(press.id),
                ),
            )

            val vm = viewModel().also { it.refresh() }

            assertEquals(
                true,
                vm.state.value
                    ?.today
                    ?.started,
            )
        }

    @Test
    fun the_date_speaks_english_when_entered_again() {
        val vm = viewModel().also { it.refresh() }

        inEnglish {
            vm.refresh()
            assertEquals(
                "Tuesday, 14 November 2023",
                vm.state.value
                    ?.today
                    ?.date,
            )
        }
    }

    @Test
    fun another_day_s_visit_is_not_today_s() =
        runTest {
            setOn(
                Visit(VisitId.random(), null, gym.today.plusDays(-1), t0 - 1.days, t0, false),
                at = t0 - 1.days,
            )

            val vm = viewModel().also { it.refresh() }

            assertEquals(
                false,
                vm.state.value
                    ?.today
                    ?.started,
            )
        }

    @Test
    fun a_visit_no_client_has_dated_counts_for_the_day_it_was_recorded_on() =
        runTest {
            setOn(Visit(VisitId.random(), null, null, t0, t0, false))

            val vm = viewModel().also { it.refresh() }

            assertEquals(
                true,
                vm.state.value
                    ?.today
                    ?.started,
            )
        }

    @Test
    fun a_finished_sync_shows_the_visit_it_pulled() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            setOn(Visit(VisitId.random(), null, gym.today, t0, t0, false))

            gym.sync.completePass()

            assertEquals(
                true,
                vm.state.value
                    ?.today
                    ?.started,
            )
        }
}
