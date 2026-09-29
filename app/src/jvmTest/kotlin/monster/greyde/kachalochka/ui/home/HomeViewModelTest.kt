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
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.strings.inEnglish
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        HomeViewModel(
            gym.visits,
            gym.sets,
            gym.machines,
            gym.currentUser,
            gym.clock,
            gym.utcOffset,
            gym.sync,
            gym.profiles,
        )

    @Test
    fun a_day_without_sets_offers_to_record_one() {
        val vm = viewModel().also { it.refresh() }

        assertEquals(HomeUiState(TodayUi(gym.today, null, null)), vm.state.value)
    }

    @Test
    fun today_s_visit_is_summarised_with_its_last_set() =
        runTest {
            val visit = Visit(VisitId.random(), null, gym.today, t0, t0, false)
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
                        0,
                        t0 + i.minutes,
                        t0,
                        false,
                    ),
                )
            }

            val vm = viewModel().also { it.refresh() }

            assertEquals(
                TodayUi(gym.today, "2 упражнения · 3 подхода", "Жим ногами 70 кг × 10"),
                vm.state.value?.today,
            )
        }

    @Test
    fun a_new_language_rewrites_today_s_summary() =
        runTest {
            val visit = Visit(VisitId.random(), null, gym.today, t0, t0, false)
            val press = Machine.new("Жим ногами", null, t0)
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    visit.id,
                    press.id,
                    70.0,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                ),
            )
            val vm = viewModel().also { it.refresh() }

            inEnglish {
                assertEquals(
                    "1 machine · 1 set",
                    vm.state.value
                        ?.today
                        ?.counts,
                )
                assertEquals(
                    "Жим ногами 70 kg × 10",
                    vm.state.value
                        ?.today
                        ?.lastSet,
                )
            }
        }

    @Test
    fun the_last_set_names_a_custom_unit() =
        runTest {
            val visit = Visit(VisitId.random(), null, gym.today, t0, t0, false)
            val gravitron =
                Machine
                    .new("Гравитрон", null, t0)
                    .copy(unit = WeightUnit.Custom, unitLabel = "плитка")
            gym.visits.upsert(visit)
            gym.machines.upsert(gravitron)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    visit.id,
                    gravitron.id,
                    7.0,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                ),
            )

            val vm = viewModel().also { it.refresh() }

            assertEquals(
                "Гравитрон 7 плитка × 10",
                vm.state.value
                    ?.today
                    ?.lastSet,
            )
        }

    @Test
    fun the_last_set_reads_in_the_unit_chosen_in_the_profile() =
        runTest {
            val visit = Visit(VisitId.random(), null, gym.today, t0, t0, false)
            val cable = Machine.new("Кроссовер", null, t0).copy(unit = WeightUnit.Lb)
            gym.visits.upsert(visit)
            gym.machines.upsert(cable)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    visit.id,
                    cable.id,
                    90.0,
                    8,
                    0,
                    t0,
                    t0,
                    false,
                ),
            )
            val vm = viewModel().also { it.refresh() }

            assertEquals(
                "Кроссовер 41 кг × 8",
                vm.state.value
                    ?.today
                    ?.lastSet,
            )

            gym.profiles.upsert(Profile.new(null, t0).copy(weightUnit = PreferredWeightUnit.Mixed))
            vm.refresh()

            assertEquals(
                "Кроссовер 90 lb × 8",
                vm.state.value
                    ?.today
                    ?.lastSet,
            )
        }

    @Test
    fun another_day_s_visit_is_not_today_s() =
        runTest {
            val yesterday = gym.today.plusDays(-1)
            val visit = Visit(VisitId.random(), null, yesterday, t0 - 1.days, t0, false)
            val press = Machine.new("Жим ногами", null, t0)
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    visit.id,
                    press.id,
                    70.0,
                    10,
                    0,
                    t0 - 1.days,
                    t0,
                    false,
                ),
            )

            val vm = viewModel().also { it.refresh() }

            assertEquals(TodayUi(gym.today, null, null), vm.state.value?.today)
        }

    @Test
    fun a_visit_no_client_has_dated_counts_for_the_day_it_was_recorded_on() =
        runTest {
            val visit = Visit(VisitId.random(), null, null, t0, t0, false)
            val press = Machine.new("Жим ногами", null, t0)
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    visit.id,
                    press.id,
                    70.0,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                ),
            )

            val vm = viewModel().also { it.refresh() }

            assertEquals(
                "1 упражнение · 1 подход",
                vm.state.value
                    ?.today
                    ?.counts,
            )
        }

    @Test
    fun a_finished_sync_shows_the_sets_it_pulled() =
        runTest {
            val vm = viewModel().also { it.refresh() }
            val visit = Visit(VisitId.random(), null, gym.today, t0, t0, false)
            val press = Machine.new("Жим ногами", null, t0)
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            val id = WorkoutSetId.random()
            gym.sets.upsert(WorkoutSet(id, null, visit.id, press.id, 70.0, 10, 0, t0, t0, false))

            gym.sync.completePass()

            assertEquals(
                "1 упражнение · 1 подход",
                vm.state.value
                    ?.today
                    ?.counts,
            )
        }
}
