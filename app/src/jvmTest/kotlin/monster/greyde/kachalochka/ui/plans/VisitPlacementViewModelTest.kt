package monster.greyde.kachalochka.ui.plans

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class VisitPlacementViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)
    private val curl = Machine.new("Сгибание рук", null, t0)
    private val legs = Plan(PlanId.random(), null, "Ноги", listOf(row.id), t0, t0, false)
    private val visit = Visit(VisitId.random(), null, gym.today, t0, t0, false, listOf(curl.id))

    private fun set(
        machine: Machine,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        null,
        visit.id,
        machine.id,
        60.0,
        10,
        0,
        t0 + minutes.minutes,
        t0,
        false,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking {
            listOf(press, row, curl).forEach { gym.machines.upsert(it) }
            gym.plans.upsert(legs)
            gym.visits.upsert(visit)
            gym.sets.upsert(set(press, 0))
            gym.sets.upsert(set(row, 1))
        }
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        VisitPlacementViewModel(
            gym.plans,
            gym.visits,
            gym.machines,
            gym.sets,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.utcOffset,
        )

    @Test
    fun the_choice_lists_every_plan_by_its_title() {
        val vm = viewModel()

        vm.choosePlan(gym.today)

        assertEquals(listOf(PlanChoiceUi(legs.id, "Ноги")), vm.state.value.choices)
    }

    @Test
    fun a_chosen_plan_takes_the_visit_s_machines_it_lacks_at_its_end_and_says_so() {
        val vm = viewModel()

        vm.choosePlan(gym.today)
        vm.addTo(legs.id)

        assertEquals(
            listOf(row.id, press.id, curl.id),
            gym.plans.rows
                .getValue(legs.id)
                .machineIds,
        )
        assertNull(vm.state.value.choices)
        assertEquals(
            VisitPlacementNoticeUi(gym.today, "Добавлено в «Ноги»"),
            vm.state.value.notice,
        )
    }

    @Test
    fun a_new_plan_opens_for_the_visit_s_day() {
        val vm = viewModel()
        var opened: CalendarDay? = null

        vm.choosePlan(gym.today)
        vm.newPlan { opened = it }

        assertEquals(gym.today, opened)
        assertNull(vm.state.value.choices)
    }

    @Test
    fun a_choice_made_before_a_switch_writes_nothing() {
        val vm = viewModel()
        vm.choosePlan(gym.today)
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)

        vm.addTo(legs.id)

        assertEquals(legs, gym.plans.rows.getValue(legs.id))
        assertNull(vm.state.value.notice)
    }
}
