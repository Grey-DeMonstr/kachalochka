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
import monster.greyde.kachalochka.ui.timer.RestTimer
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

    @Test
    fun a_plan_shown_before_a_switch_is_not_started_for_the_new_account() {
        val vm =
            PlansViewModel(
                gym.plans,
                gym.visits,
                gym.machines,
                gym.sets,
                gym.currentUser,
                gym.accounts,
                gym.clock,
                gym.utcOffset,
                RestTimer(gym.clock),
                gym.sync,
            )
        assertEquals(1, vm.state.value.rows.size)
        vm.askToStart(legs.id)
        val reload = CompletableDeferred<Unit>()
        gym.machines.readGate = reload
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
        var started = false

        vm.start { started = true }
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
                family.visits,
                family.machines,
                family.sets,
                family.currentUser,
                family.accounts,
                family.clock,
                family.utcOffset,
                RestTimer(family.clock),
                family.sync,
            )
        var opened: CalendarDay? = null

        vm.askToStart(sashaLegs.id)
        vm.start { opened = it }

        assertNotNull(opened)
        assertEquals(
            listOf(sashaPress.id),
            runBlocking { family.visits.onDay(SASHA.userId, family.today) }?.planned,
        )
    }
}
