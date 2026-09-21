package monster.greyde.kachalochka.ui.visit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class VisitViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val visit = Visit(VisitId.random(), null, t0, null, t0, false)
    private val yesterday = VisitId.random()
    private val press =
        Machine
            .new(
                "Жим ногами",
                null,
                t0,
            ).copy(platformWeight = 20.0, setupNote = "Сиденье на 4")
    private val row = Machine.new("Тяга верхнего блока", null, t0)
    private val timer = RestTimer(gym.clock)
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
    private val ivanVisit = Visit(VisitId.random(), ivan.account.userId, t0, null, t0, false)
    private val ivanPress =
        Machine
            .new("Жим ногами", ivan.account.userId, t0)
            .copy(weightStep = 5.0, setupNote = "Сиденье на 4")

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    /** Иван and Миша signed in, Иван active on his own visit and his own machine. */
    private fun twoAccountGym(): FakeGym =
        FakeGym().withAccounts(ivan, misha, active = ivan).also {
            runBlocking {
                it.visits.upsert(ivanVisit)
                it.machines.upsert(ivanPress)
            }
        }

    private fun set(
        visitId: VisitId,
        machine: Machine,
        weight: Double,
        reps: Int,
        minutes: Int,
        owner: UserId? = null,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        owner,
        visitId,
        machine.id,
        weight,
        reps,
        t0 + minutes.minutes,
        t0,
        false,
    )

    private fun viewModel(
        gym: FakeGym = this.gym,
        visitId: VisitId = visit.id,
    ) = VisitViewModel(
        visitId,
        gym.visits,
        gym.machines,
        gym.sets,
        gym.currentUser,
        gym.accounts,
        timer,
        gym.clock,
        gym.utcOffset,
    )

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            gym.machines.upsert(row)
            listOf(70.0 to 10, 70.0 to 10, 75.0 to 8).forEachIndexed { i, (w, r) ->
                gym.sets.upsert(set(yesterday, press, w, r, -(1.days.inWholeMinutes.toInt()) + i))
            }
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun before_a_machine_is_chosen_the_sheet_is_empty() {
        val vm = viewModel().also { it.refresh() }

        val state = assertNotNull(vm.state.value)
        assertNull(state.sheet)
        assertEquals("0 подходов", state.setCountLabel)
    }

    @Test
    fun choosing_a_machine_seeds_the_steppers_from_the_previous_visit() {
        val vm = viewModel().also { it.selectMachine(press.id) }

        val sheet = assertNotNull(vm.state.value?.sheet)
        assertEquals("Жим ногами", sheet.name)
        assertEquals("(+20 кг)", sheet.platformSuffix)
        assertEquals("подход 1", sheet.setNumberLabel)
        assertEquals("Сиденье на 4", sheet.caption)
        assertEquals("Вчера · 70×10 · 70×10 · 75×8", sheet.previous)
        assertEquals("70", sheet.weight)
        assertEquals("кг всего · ±2,5", sheet.weightCaption)
        assertEquals("10", sheet.reps)
    }

    @Test
    fun the_steppers_move_by_the_machine_step_and_by_one_rep() {
        val vm = viewModel().also { it.selectMachine(press.id) }

        vm.changeWeight(+1)
        vm.changeReps(-1)

        val sheet = assertNotNull(vm.state.value?.sheet)
        assertEquals("72,5", sheet.weight)
        assertEquals("9", sheet.reps)
    }

    @Test
    fun a_refresh_keeps_the_stepper_values() {
        val vm = viewModel().also { it.selectMachine(press.id) }
        vm.changeWeight(+1)
        vm.changeReps(-1)

        vm.refresh()

        val sheet = assertNotNull(vm.state.value?.sheet)
        assertEquals("72,5", sheet.weight)
        assertEquals("9", sheet.reps)
    }

    @Test
    fun saving_records_the_set_starts_the_rest_and_moves_to_the_next_set() =
        runTest {
            val vm = viewModel().also { it.selectMachine(press.id) }
            vm.changeWeight(-1)

            vm.save()

            val saved = gym.sets.forVisit(visit.id).single()
            assertEquals(67.5 to 10, saved.weight to saved.reps)
            assertEquals(t0, timer.startedAt.value)
            val state = assertNotNull(vm.state.value)
            assertEquals("1 подход", state.setCountLabel)
            assertEquals("подход 2", state.sheet?.setNumberLabel)
            assertEquals("70", state.sheet?.weight)
            assertEquals(
                listOf("Жим ногами (+20 кг)" to "67,5 кг"),
                state.groups.map { it.title to it.summary },
            )
        }

    @Test
    fun a_second_tap_while_saving_records_one_set() =
        runTest {
            val vm = viewModel().also { it.selectMachine(press.id) }
            val gate = CompletableDeferred<Unit>().also { gym.sets.gate = it }

            vm.save()
            vm.save()
            gate.complete(Unit)

            assertEquals(1, gym.sets.forVisit(visit.id).size)
        }

    @Test
    fun a_group_expands_into_its_sets() =
        runTest {
            gym.sets.upsert(set(visit.id, row, 45.0, 12, 1))
            gym.sets.upsert(set(visit.id, row, 45.0, 10, 2))
            val vm = viewModel().also { it.refresh() }

            vm.toggleGroup(row.id)

            val group = assertNotNull(vm.state.value).groups.single()
            assertEquals("2 × 45 кг", group.summary)
            assertEquals(true, group.expanded)
            assertEquals(
                listOf(
                    "Тяга верхнего блока · подход 1" to "45 кг × 12",
                    "Тяга верхнего блока · подход 2" to "45 кг × 10",
                ),
                group.sets.map { it.title to it.value },
            )
        }

    @Test
    fun ending_the_visit_stamps_its_end() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            var ended = false

            vm.endVisit { ended = true }

            assertEquals(t0, gym.visits.byId(visit.id)?.endedAt)
            assertEquals(true, ended)
        }

    @Test
    fun editing_a_set_shows_what_was_recorded_and_when() =
        runTest {
            val first = set(visit.id, press, 60.0, 10, 0)
            val second = set(visit.id, press, 70.0, 10, 1)
            gym.sets.upsert(first)
            gym.sets.upsert(second)
            val vm = viewModel().also { it.refresh() }

            vm.editSet(second.id)

            val state = assertNotNull(vm.state.value)
            val sheet = assertNotNull(state.sheet)
            assertEquals(true, sheet.editing)
            assertEquals("подход 2", sheet.setNumberLabel)
            assertEquals("Правка · записано 22:14, было 70 кг × 10", sheet.caption)
            assertNull(sheet.previous)
            assertEquals("70", sheet.weight)
            assertEquals(
                listOf(false, true),
                state.groups
                    .single()
                    .sets
                    .map { it.selected },
            )
        }

    @Test
    fun saving_an_edit_rewrites_the_set_without_restarting_the_rest() =
        runTest {
            val recorded = set(visit.id, press, 70.0, 10, 0)
            gym.sets.upsert(recorded)
            val vm = viewModel().also { it.refresh() }
            vm.editSet(recorded.id)

            vm.changeWeight(+1)
            vm.save()

            val saved = gym.sets.forVisit(visit.id).single()
            assertEquals(recorded.id, saved.id)
            assertEquals(72.5, saved.weight)
            assertNull(timer.startedAt.value)
            assertEquals(
                false,
                vm.state.value
                    ?.sheet
                    ?.editing,
            )
        }

    @Test
    fun deleting_the_edited_set_removes_it_from_the_visit() =
        runTest {
            val recorded = set(visit.id, press, 70.0, 10, 0)
            gym.sets.upsert(recorded)
            val vm = viewModel().also { it.refresh() }
            vm.editSet(recorded.id)

            vm.deleteEditedSet()

            assertEquals(emptyList(), gym.sets.forVisit(visit.id))
            assertEquals(true, gym.sets.rows[recorded.id]?.deleted)
            assertEquals("0 подходов", vm.state.value?.setCountLabel)
        }

    @Test
    fun leaving_edit_mode_returns_to_adding_on_the_same_machine() =
        runTest {
            val recorded = set(visit.id, press, 70.0, 10, 0)
            gym.sets.upsert(recorded)
            val vm = viewModel().also { it.refresh() }
            vm.editSet(recorded.id)

            assertEquals(true, vm.leaveEdit())
            assertEquals(false, vm.leaveEdit())
            assertEquals(
                "подход 2",
                vm.state.value
                    ?.sheet
                    ?.setNumberLabel,
            )
        }

    @Test
    fun saving_as_another_account_records_into_that_account_s_own_visit() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two, ivanVisit.id).also { it.selectMachine(ivanPress.id) }

            vm.switchTo(misha.account.userId)
            vm.save()

            val saved =
                two.sets.rows.values
                    .single()
            assertEquals(misha.account.userId, saved.userId)
            assertEquals(two.visits.active(misha.account.userId)?.id, saved.visitId)
            assertNotEquals(ivanVisit.id, saved.visitId)
        }

    @Test
    fun switching_shows_the_other_account_s_visit_without_creating_one() =
        runTest {
            val two = twoAccountGym()
            two.sets.upsert(set(ivanVisit.id, ivanPress, 70.0, 10, 0, ivan.account.userId))
            val vm = viewModel(two, ivanVisit.id).also { it.selectMachine(ivanPress.id) }
            assertEquals("1 подход", vm.state.value?.setCountLabel)

            vm.switchTo(misha.account.userId)

            assertEquals("0 подходов", vm.state.value?.setCountLabel)
            assertEquals(
                listOf(ivanVisit.id),
                two.visits.rows.keys
                    .toList(),
            )
        }

    @Test
    fun the_visit_a_save_lands_in_is_the_one_on_screen() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two, ivanVisit.id).also { it.selectMachine(ivanPress.id) }
            vm.switchTo(misha.account.userId)

            vm.save()

            val saved =
                two.sets.rows.values
                    .single()
            val state = assertNotNull(vm.state.value)
            assertEquals("1 подход", state.setCountLabel)
            assertEquals(
                listOf(saved.id),
                state.groups
                    .single()
                    .sets
                    .map { it.id },
            )
            assertEquals(emptyList(), two.sets.forVisit(ivanVisit.id))
        }

    @Test
    fun the_sheet_names_the_account_a_save_would_record_as() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two, ivanVisit.id).also { it.selectMachine(ivanPress.id) }
            assertEquals(
                "Сохранить · Иван",
                vm.state.value
                    ?.sheet
                    ?.saveLabel,
            )

            vm.switchTo(misha.account.userId)

            val sheet = assertNotNull(vm.state.value?.sheet)
            assertEquals("Сохранить · Миша", sheet.saveLabel)
            assertEquals(
                listOf("Иван" to false, "Миша" to true),
                sheet.people.map {
                    it.displayName to it.active
                },
            )
        }

    @Test
    fun saving_as_another_account_mirrors_the_machine_to_them() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two, ivanVisit.id).also { it.selectMachine(ivanPress.id) }

            vm.switchTo(misha.account.userId)
            vm.save()

            val mishaPress = assertNotNull(two.machines.named(misha.account.userId, "Жим ногами"))
            assertNotEquals(ivanPress.id, mishaPress.id)
            assertEquals(ivanPress.weightStep, mishaPress.weightStep)
            assertEquals(
                mishaPress.id,
                two.sets.rows.values
                    .single()
                    .machineId,
            )
        }

    @Test
    fun a_second_set_as_the_same_account_reuses_the_mirrored_machine() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two, ivanVisit.id).also { it.selectMachine(ivanPress.id) }
            vm.switchTo(misha.account.userId)

            vm.save()
            vm.save()

            assertEquals(2, two.sets.rows.size)
            assertEquals(
                1,
                two.machines.rows.values
                    .count { it.userId == misha.account.userId },
            )
        }

    @Test
    fun opening_machine_settings_after_a_switch_mirrors_the_machine_first() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two, ivanVisit.id).also { it.selectMachine(ivanPress.id) }
            vm.switchTo(misha.account.userId)
            var opened: MachineId? = null

            vm.openMachineSettings { opened = it }

            val mishaPress = assertNotNull(two.machines.named(misha.account.userId, "Жим ногами"))
            assertEquals(mishaPress.id, opened)
            assertNotEquals(ivanPress.id, opened)
            assertEquals(ivanPress.setupNote, mishaPress.setupNote)
        }

    @Test
    fun an_edit_keeps_the_set_with_its_own_owner_and_visit() =
        runTest {
            val two = twoAccountGym()
            val recorded = set(ivanVisit.id, ivanPress, 70.0, 10, 0, ivan.account.userId)
            two.sets.upsert(recorded)
            val vm = viewModel(two, ivanVisit.id).also { it.refresh() }
            vm.editSet(recorded.id)

            two.accounts.switchTo(misha.account.userId)
            vm.changeWeight(+1)
            vm.save()

            val saved = two.sets.rows.getValue(recorded.id)
            assertEquals(75.0, saved.weight)
            assertEquals(ivan.account.userId, saved.userId)
            assertEquals(ivanVisit.id, saved.visitId)
            assertEquals(ivanPress.id, saved.machineId)
            assertEquals(
                listOf(ivanVisit.id),
                two.visits.rows.keys
                    .toList(),
            )
        }

    @Test
    fun ending_a_visit_the_switched_to_account_never_started_just_leaves() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two, ivanVisit.id).also { it.refresh() }
            vm.switchTo(misha.account.userId)
            var ended = false

            vm.endVisit { ended = true }

            assertEquals(true, ended)
            assertEquals(
                listOf(ivanVisit.id),
                two.visits.rows.keys
                    .toList(),
            )
            assertNull(two.visits.byId(ivanVisit.id)?.endedAt)
        }
}
