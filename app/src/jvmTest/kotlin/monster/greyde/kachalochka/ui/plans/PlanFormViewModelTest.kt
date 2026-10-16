package monster.greyde.kachalochka.ui.plans

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.account.HeldWrites
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.olegTrainedOn
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlanFormViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)
    private val legs = Plan(PlanId.random(), null, "Ноги", listOf(press.id, row.id), t0, t0, false)

    private val main = UnconfinedTestDispatcher()
    private val held = HeldWrites()

    private fun viewModel(
        id: PlanId?,
        fromVisit: CalendarDay? = null,
        machine: MachineId? = null,
        gym: FakeGym = this.gym,
    ) = PlanFormViewModel(
        id,
        fromVisit,
        machine,
        gym.plans,
        gym.visits,
        gym.sets,
        gym.utcOffset,
        gym.accounts,
        gym.clock,
        gym.catalogue,
        gym.machineReads,
        FailureLog { gym.failures += it },
        held,
    )

    private fun newPlans() =
        gym.plans.rows.values
            .filter { it.id != legs.id }

    private fun stored() = gym.plans.rows.getValue(legs.id)

    @Test
    fun a_linked_friend_s_photo_stands_in_for_a_machine_without_one() =
        runTest {
            val signed = signedInGym()
            val mine = Machine.new("Жим ногами", ME.userId, t0)
            signed.machines.upsert(mine)
            signed.olegTrainedOn(mine, ME)
            val olegPress = signed.friends.machines.single { it.userId == OLEG.userId }
            val olegs = Photo.new(olegPress.id, OLEG.userId, t0)
            signed.friends.photos += olegs
            val plan = Plan(PlanId.random(), ME.userId, "Ноги", listOf(mine.id), t0, t0, false)
            signed.plans.upsert(plan)

            val vm = viewModel(plan.id, gym = signed)

            assertEquals(
                olegs,
                vm.state.value.rows
                    .single()
                    .photo,
            )
        }

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(main)
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
    fun a_plan_made_for_a_machine_holds_it_and_is_written_at_once() {
        val vm = viewModel(null, machine = row.id)

        assertEquals(
            listOf(row.id),
            vm.state.value.rows
                .map { it.id },
        )
        assertEquals(listOf(row.id), newPlans().single().machineIds)
        assertTrue(vm.state.value.canDelete)
    }

    @Test
    fun a_new_plan_is_written_at_its_first_change() {
        val vm = viewModel(null)
        assertEquals(emptyList(), newPlans())

        vm.add(press.id)

        val written = newPlans().single()
        assertEquals(listOf(press.id), written.machineIds)
        assertEquals("", written.name)
        assertTrue(vm.state.value.canDelete)
    }

    @Test
    fun a_named_plan_may_lose_every_machine() {
        val vm = viewModel(legs.id)

        vm.remove(press.id)
        vm.remove(row.id)

        assertEquals(legs.copy(machineIds = emptyList()), stored())
    }

    @Test
    fun a_plan_left_without_name_and_machines_is_deleted_at_once_and_a_change_brings_it_back() {
        val vm = viewModel(legs.id)
        vm.typeName("")
        vm.remove(press.id)
        assertFalse(stored().deleted)

        vm.remove(row.id)
        assertTrue(stored().deleted)
        assertFalse(vm.state.value.canDelete)
        vm.add(press.id)

        assertEquals(legs.copy(name = "", machineIds = listOf(press.id)), stored())
        assertTrue(vm.state.value.canDelete)
    }

    @Test
    fun the_name_is_written_after_a_pause_in_typing() {
        val vm = viewModel(legs.id)

        vm.typeName("Сп")
        main.scheduler.advanceTimeBy(NAME_PAUSE - 1.milliseconds)
        vm.typeName("Спина")
        main.scheduler.advanceTimeBy(NAME_PAUSE - 1.milliseconds)
        assertEquals("Ноги", stored().name)
        main.scheduler.advanceTimeBy(2.milliseconds)

        assertEquals("Спина", stored().name)
        assertFalse(vm.state.value.done)
    }

    @Test
    fun switching_accounts_closes_the_form_without_writing() {
        val vm = viewModel(legs.id)
        vm.typeName("Спина")

        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
        main.scheduler.advanceTimeBy(NAME_PAUSE * 2)

        assertTrue(vm.state.value.done)
        assertEquals(legs, stored())
    }

    @Test
    fun a_switch_from_the_account_menu_writes_the_typed_name_first() =
        runTest(main) {
            val vm = viewModel(legs.id)
            vm.typeName("Спина")

            held.writeAll()
            gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)

            assertTrue(vm.state.value.done)
            assertEquals("Спина", stored().name)
        }

    @Test
    fun switching_accounts_while_a_new_plan_is_read_writes_nothing() {
        val read = CompletableDeferred<Unit>()
        gym.machines.readGate = read
        val vm = viewModel(null, machine = row.id)

        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)
        read.complete(Unit)

        assertTrue(vm.state.value.done)
        assertEquals(emptyList(), newPlans())
    }

    @Test
    fun a_failed_write_is_recorded_and_the_next_write_still_runs() {
        val vm = viewModel(legs.id)
        val refused = IllegalStateException("refused")
        gym.plans.failure = refused

        vm.remove(press.id)
        assertEquals(listOf<Throwable>(refused), gym.failures)
        gym.plans.failure = null
        vm.remove(row.id)

        assertEquals(emptyList(), stored().machineIds)
    }

    @Test
    fun a_failed_write_on_leaving_still_closes_the_form() {
        val vm = viewModel(legs.id)
        vm.typeName("Спина")
        gym.plans.failure = IllegalStateException("refused")

        vm.leave()

        assertTrue(vm.state.value.done)
        assertEquals(legs, stored())
    }

    @Test
    fun a_reorder_is_written_at_once() {
        val vm = viewModel(legs.id)

        vm.move(0, 1)

        assertEquals(listOf(row.id, press.id), stored().machineIds)
    }

    @Test
    fun a_pin_is_written_at_once_and_a_second_tap_takes_it_off() {
        val vm = viewModel(legs.id)
        assertFalse(vm.state.value.pinned)

        vm.togglePin()
        assertTrue(vm.state.value.pinned)
        assertEquals(legs.copy(pinned = true), stored())
        vm.togglePin()

        assertFalse(vm.state.value.pinned)
        assertEquals(legs, stored())
    }

    @Test
    fun a_pinned_plan_opens_pinned_and_keeps_its_pin_through_other_writes() =
        runTest {
            gym.plans.upsert(legs.copy(pinned = true))
            val vm = viewModel(legs.id)
            assertTrue(vm.state.value.pinned)

            vm.remove(press.id)

            assertEquals(legs.copy(machineIds = listOf(row.id), pinned = true), stored())
        }

    @Test
    fun a_pin_waits_in_line_behind_earlier_writes() {
        val vm = viewModel(null)
        val write = CompletableDeferred<Unit>()
        gym.plans.gate = write

        vm.add(press.id)
        vm.togglePin()
        write.complete(Unit)

        val written = newPlans().single()
        assertTrue(written.pinned)
        assertEquals(listOf(press.id), written.machineIds)
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
    fun leaving_a_plan_whose_name_was_cleared_deletes_it_when_it_has_no_machines() {
        val vm = viewModel(legs.id)
        vm.remove(press.id)
        vm.remove(row.id)
        vm.typeName(" ")
        assertFalse(stored().deleted)

        vm.leave()

        assertTrue(stored().deleted)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun writes_run_in_order_and_leaving_waits_for_them() {
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
            assertTrue(vm.state.value.done)
            vm.leave()

            assertEquals(mapOf(deleted.id to deleted), gym.plans.rows)
        }
}
