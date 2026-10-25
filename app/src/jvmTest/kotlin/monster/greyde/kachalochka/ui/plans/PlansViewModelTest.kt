package monster.greyde.kachalochka.ui.plans

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val legs = Plan(PlanId.random(), null, "Ноги", listOf(press.id), t0, t0, false)

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            gym.machines.upsert(press)
            gym.plans.upsert(legs)
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        PlansViewModel(
            gym.plans,
            gym.machines,
            gym.placement,
            gym.currentUser,
            gym.accounts,
            gym.sync,
        )

    @Test
    fun pinned_plans_list_first_and_say_they_are_pinned() =
        runTest {
            val back = Plan(PlanId.random(), null, "Спина", listOf(press.id), t0, t0, false, true)
            gym.plans.upsert(back)

            val rows = viewModel().state.value.rows

            assertEquals(
                listOf(back.id to true, legs.id to false),
                rows.map { it.id to it.pinned },
            )
        }

    @Test
    fun a_pinned_plan_starts_without_asking_and_stays() =
        runTest {
            val pinned = legs.copy(pinned = true)
            gym.plans.upsert(pinned)
            val vm = viewModel()
            var opened: CalendarDay? = null

            vm.start(legs.id) { opened = it }

            assertEquals(null, vm.state.value.confirmingStart)
            assertEquals(gym.today, opened)
            assertEquals(listOf(press.id), gym.visits.onDay(null, gym.today)?.planned)
            assertEquals(pinned, gym.plans.rows.getValue(legs.id))
            assertEquals(
                listOf(legs.id),
                vm.state.value.rows
                    .map { it.id },
            )
        }

    @Test
    fun a_plan_that_is_not_pinned_asks_first_and_goes_once_started() =
        runTest {
            val vm = viewModel()
            var opened: CalendarDay? = null

            vm.start(legs.id) { opened = it }
            assertEquals(legs.id, vm.state.value.confirmingStart)
            assertEquals(null, opened)
            vm.confirmStart { opened = it }

            assertEquals(gym.today, opened)
            assertTrue(
                gym.plans.rows
                    .getValue(legs.id)
                    .deleted,
            )
        }

    @Test
    fun a_plan_shown_before_a_switch_is_not_started_for_the_new_account() {
        val vm =
            PlansViewModel(
                gym.plans,
                gym.machines,
                gym.placement,
                gym.currentUser,
                gym.accounts,
                gym.sync,
            )
        assertEquals(1, vm.state.value.rows.size)
        var started = false
        vm.start(legs.id) { started = true }
        assertEquals(legs.id, vm.state.value.confirmingStart)
        val reload = CompletableDeferred<Unit>()
        gym.machines.readGate = reload
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)

        vm.confirmStart { started = true }
        reload.complete(Unit)

        assertFalse(started)
        assertTrue(gym.visits.rows.isEmpty())
        assertFalse(
            gym.plans.rows
                .getValue(legs.id)
                .deleted,
        )
    }

    @Test
    fun a_plan_started_for_a_managed_child_lands_in_the_child_s_visit() {
        val family =
            FakeGym()
                .withAccounts(IVAN_SESSION, active = IVAN_SESSION)
                .withChild(childAccount(SASHA, IVAN_SESSION))
        val sashaPress = Machine.new("Жим ногами", SASHA.userId, t0)
        val sashaLegs =
            Plan(PlanId.random(), SASHA.userId, "Ноги", listOf(sashaPress.id), t0, t0, false)
        runBlocking {
            family.accounts.switchTo(SASHA.userId)
            family.machines.upsert(sashaPress)
            family.plans.upsert(sashaLegs)
        }
        val vm =
            PlansViewModel(
                family.plans,
                family.machines,
                family.placement,
                family.currentUser,
                family.accounts,
                family.sync,
            )
        var opened: CalendarDay? = null

        vm.start(sashaLegs.id) { opened = it }
        vm.confirmStart { opened = it }

        assertNotNull(opened)
        assertEquals(
            listOf(sashaPress.id),
            runBlocking { family.visits.onDay(SASHA.userId, family.today) }?.planned,
        )
    }
}
