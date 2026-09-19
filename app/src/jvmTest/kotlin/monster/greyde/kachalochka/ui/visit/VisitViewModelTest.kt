package monster.greyde.kachalochka.ui.visit

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
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun set(
        visitId: VisitId,
        machine: Machine,
        weight: Double,
        reps: Int,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        null,
        visitId,
        machine.id,
        weight,
        reps,
        t0 + minutes.minutes,
        t0,
        false,
    )

    private fun viewModel() =
        VisitViewModel(
            visit.id,
            gym.visits,
            gym.machines,
            gym.sets,
            gym.currentUser,
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

        assertEquals(
            "72,5",
            vm.state.value
                ?.sheet
                ?.weight,
        )
        assertEquals(
            "9",
            vm.state.value
                ?.sheet
                ?.reps,
        )
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
                state.groups.map {
                    it.title to
                        it.summary
                },
            )
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
}
