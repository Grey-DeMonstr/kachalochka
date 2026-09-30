package monster.greyde.kachalochka.ui.plans

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val reload = CompletableDeferred<Unit>()
        gym.machines.readGate = reload
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
        var started = false

        vm.start(legs.id) { started = true }
        reload.complete(Unit)

        assertFalse(started)
        assertTrue(gym.visits.rows.isEmpty())
        assertFalse(
            gym.plans.rows
                .getValue(legs.id)
                .deleted,
        )
    }
}
