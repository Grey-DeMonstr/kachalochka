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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlanFormViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)
    private val legs = Plan(PlanId.random(), null, "Ноги", listOf(press.id, row.id), t0, t0, false)

    private fun viewModel(id: PlanId?) =
        PlanFormViewModel(id, gym.plans, gym.accounts, gym.clock, gym.catalogue)

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            gym.machines.upsert(press)
            gym.machines.upsert(row)
            gym.plans.upsert(legs)
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_reordered_plan_is_saved_in_its_new_order() {
        val vm = viewModel(legs.id)

        vm.move(0, 1)
        vm.save()

        assertEquals(
            listOf(row.id, press.id),
            gym.plans.rows
                .getValue(legs.id)
                .machineIds,
        )
    }

    @Test
    fun a_save_right_after_adding_a_new_machine_keeps_it() =
        runTest {
            val fresh = Machine.new("Гакк", null, t0)
            val vm = viewModel(legs.id)
            gym.machines.upsert(fresh)
            val read = CompletableDeferred<Unit>()
            gym.machines.readGate = read

            vm.add(fresh.id)
            vm.save()
            read.complete(Unit)

            assertEquals(
                listOf(press.id, row.id, fresh.id),
                gym.plans.rows
                    .getValue(legs.id)
                    .machineIds,
            )
        }

    @Test
    fun opening_a_deleted_plan_closes_the_form_without_writing() =
        runTest {
            val deleted = legs.copy(deleted = true)
            gym.plans.upsert(deleted)

            val vm = viewModel(deleted.id)

            assertTrue(vm.state.value.done)
            assertEquals(mapOf(deleted.id to deleted), gym.plans.rows)
        }
}
