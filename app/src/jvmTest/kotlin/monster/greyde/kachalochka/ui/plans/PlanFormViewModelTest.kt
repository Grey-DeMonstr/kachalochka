package monster.greyde.kachalochka.ui.plans

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlanFormViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)
    private val legs = Plan(PlanId.random(), null, "Ноги", listOf(press.id, row.id), t0, t0, false)

    private fun viewModel(
        id: PlanId?,
        fromVisit: CalendarDay? = null,
    ) = PlanFormViewModel(
        id,
        fromVisit,
        gym.plans,
        gym.visits,
        gym.sets,
        gym.utcOffset,
        gym.accounts,
        gym.clock,
        gym.catalogue,
    )

    private fun newPlans() =
        gym.plans.rows.values
            .filter { it.id != legs.id }

    private fun stored() = gym.plans.rows.getValue(legs.id)

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
    fun a_plan_from_a_visit_is_written_with_its_machines_in_visit_order_at_once() =
        runTest {
            val today = gym.today
            val visit = Visit(VisitId.random(), null, today, t0, t0, false, listOf(press.id))
            gym.visits.upsert(visit)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    visit.id,
                    row.id,
                    50.0,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                ),
            )
            val vm = viewModel(null, today)

            assertEquals(
                listOf(row.id, press.id),
                vm.state.value.rows
                    .map { it.id },
            )
            assertEquals(listOf(row.id, press.id), newPlans().single().machineIds)
            assertTrue(vm.state.value.canDelete)
            assertEquals(
                listOf(press.id),
                gym.visits.rows
                    .getValue(visit.id)
                    .planned,
            )
        }

    @Test
    fun a_new_plan_is_written_at_its_first_change_and_may_lose_every_machine() {
        val vm = viewModel(null)
        assertEquals(emptyList(), newPlans())

        vm.add(press.id)
        val written = newPlans().single()
        assertEquals(listOf(press.id), written.machineIds)
        assertEquals("", written.name)
        vm.remove(press.id)

        assertEquals(written.copy(machineIds = emptyList()), newPlans().single())
    }

    @Test
    fun a_reorder_is_written_at_once() {
        val vm = viewModel(legs.id)

        vm.move(0, 1)

        assertEquals(listOf(row.id, press.id), stored().machineIds)
    }

    @Test
    fun an_added_machine_is_written_once_the_machines_are_read() =
        runTest {
            val fresh = Machine.new("Гакк", null, t0)
            val vm = viewModel(legs.id)
            gym.machines.upsert(fresh)
            val read = CompletableDeferred<Unit>()
            gym.machines.readGate = read

            vm.add(fresh.id)
            assertEquals(legs, stored())
            read.complete(Unit)

            assertEquals(listOf(press.id, row.id, fresh.id), stored().machineIds)
        }

    @Test
    fun the_name_is_written_trimmed_when_committed_not_while_typed() {
        val vm = viewModel(legs.id)

        vm.typeName("  Спина  ")
        assertEquals(legs, stored())
        vm.commitName()

        assertEquals("Спина", stored().name)
    }

    @Test
    fun leaving_writes_the_typed_name_and_closes_the_form() {
        val vm = viewModel(legs.id)
        vm.typeName("Спина")

        vm.leave()

        assertEquals("Спина", stored().name)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun leaving_unchanged_writes_nothing() {
        val vm = viewModel(legs.id)
        vm.typeName("Ноги ")
        vm.commitName()

        vm.leave()

        assertEquals(legs, stored())
        assertTrue(vm.state.value.done)
    }

    @Test
    fun leaving_a_new_plan_untouched_writes_nothing() {
        val vm = viewModel(null)
        vm.typeName(" ")

        vm.leave()

        assertEquals(emptyList(), newPlans())
        assertTrue(vm.state.value.done)
    }

    @Test
    fun leaving_a_written_plan_with_neither_name_nor_machines_deletes_it() {
        val vm = viewModel(legs.id)
        vm.typeName("")
        vm.remove(press.id)
        vm.remove(row.id)
        assertFalse(stored().deleted)

        vm.leave()

        assertTrue(stored().deleted)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun writes_wait_for_each_other_so_a_new_plan_is_created_once() {
        val vm = viewModel(null)
        val write = CompletableDeferred<Unit>()
        gym.plans.gate = write

        vm.add(press.id)
        vm.add(row.id)
        vm.typeName("Спина")
        vm.leave()
        assertFalse(vm.state.value.done)
        write.complete(Unit)

        val written = newPlans().single()
        assertEquals("Спина", written.name)
        assertEquals(listOf(press.id, row.id), written.machineIds)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun opening_a_deleted_plan_closes_the_form_without_writing() =
        runTest {
            val deleted = legs.copy(deleted = true)
            gym.plans.upsert(deleted)

            val vm = viewModel(deleted.id)
            vm.leave()

            assertTrue(vm.state.value.done)
            assertEquals(mapOf(deleted.id to deleted), gym.plans.rows)
        }
}
