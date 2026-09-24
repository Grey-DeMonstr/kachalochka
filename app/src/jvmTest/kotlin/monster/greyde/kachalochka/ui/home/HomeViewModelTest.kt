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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        HomeViewModel(gym.visits, gym.sets, gym.machines, gym.currentUser, gym.clock, gym.sync)

    @Test
    fun without_a_running_visit_home_offers_to_start_one() {
        val vm = viewModel().also { it.refresh() }

        assertEquals(HomeUiState(activeVisit = null), vm.state.value)
    }

    @Test
    fun a_running_visit_is_summarised_with_its_last_set() =
        runTest {
            val visit = Visit(VisitId.random(), null, t0, null, t0, false)
            val press = Machine.new("Жим ногами", null, t0).copy(platformWeight = 20.0)
            val row = Machine.new("Тяга", null, t0)
            gym.visits.upsert(visit)
            listOf(press, row).forEach { gym.machines.upsert(it) }
            listOf(row to 45.0, press to 60.0, press to 70.0).forEachIndexed { i, (m, w) ->
                gym.sets.upsert(
                    WorkoutSet(
                        WorkoutSetId.random(),
                        null,
                        visit.id,
                        m.id,
                        w,
                        10,
                        t0 + i.minutes,
                        t0,
                        false,
                    ),
                )
            }

            val vm = viewModel().also { it.refresh() }

            assertEquals(
                ActiveVisitUi(visit.id, "2 тренажёра · 3 подхода", "Жим ногами 70 кг × 10"),
                vm.state.value?.activeVisit,
            )
        }

    @Test
    fun a_finished_sync_shows_the_running_visit_it_pulled() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            val pulled = Visit(VisitId.random(), null, t0, null, t0, false)

            gym.visits.upsert(pulled)
            gym.sync.completePass()

            assertEquals(
                pulled.id,
                vm.state.value
                    ?.activeVisit
                    ?.id,
            )
        }

    @Test
    fun starting_a_visit_saves_it_and_hands_back_its_id() =
        runTest {
            var started: VisitId? = null

            viewModel().startVisit { started = it }

            val active = assertNotNull(gym.visits.active(null))
            assertEquals(active.id, started)
            assertEquals(t0, active.recordedAt)
        }
}
