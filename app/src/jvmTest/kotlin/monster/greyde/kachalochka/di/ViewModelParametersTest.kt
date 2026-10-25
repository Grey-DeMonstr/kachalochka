package monster.greyde.kachalochka.di

import androidx.lifecycle.ViewModel
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
import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.fakeGymModule
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.FriendCalendarViewModel
import monster.greyde.kachalochka.ui.friends.FriendVisitViewModel
import monster.greyde.kachalochka.ui.friends.GroupViewModel
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.OlegVisitFixture
import monster.greyde.kachalochka.ui.friends.signedInGym
import monster.greyde.kachalochka.ui.machine.FriendMachineViewModel
import monster.greyde.kachalochka.ui.machine.LinkChooserViewModel
import monster.greyde.kachalochka.ui.machine.MachineFormArgs
import monster.greyde.kachalochka.ui.machine.MachineFormViewModel
import monster.greyde.kachalochka.ui.machine.MachinePickerViewModel
import monster.greyde.kachalochka.ui.machine.MineChooserViewModel
import monster.greyde.kachalochka.ui.measures.BodyFatViewModel
import monster.greyde.kachalochka.ui.measures.MeasureViewModel
import monster.greyde.kachalochka.ui.measures.MeasurementFormViewModel
import monster.greyde.kachalochka.ui.plans.PlanFormViewModel
import monster.greyde.kachalochka.ui.stats.StatisticsViewModel
import monster.greyde.kachalochka.ui.visit.VisitViewModel
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The graph hands each view model what its screen passes at runtime, nulls included. */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelParametersTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val opened = mutableListOf<Koin>()
    private val koin by lazy { koinOver(gym) }

    /** A module keeps its singletons until its Koin closes, so each one opened here is closed. */
    private fun koinOver(gym: FakeGym): Koin =
        koinApplication {
            allowOverride(true)
            modules(appModule, platformModule(), fakeGymModule(gym))
        }.koin.also { opened += it }

    private inline fun <reified T : ViewModel> Koin.viewModel(vararg values: Any?): T =
        get { parametersOf(*values) }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
        Dispatchers.resetMain()
    }

    @Test
    fun a_visit_opens_on_the_day_it_was_given() =
        runTest {
            val seventh = CalendarDay(2023, 11, 7)
            gym.visits.upsert(Visit(VisitId.random(), null, seventh, t0, t0, false))

            val vm: VisitViewModel = koin.viewModel(seventh)
            vm.refresh()

            assertEquals("Вторник, 7 ноября", assertNotNull(vm.state.value).title)
        }

    @Test
    fun the_measurement_form_opens_on_the_given_day_and_on_today_without_one() {
        val saturday = CalendarDay(2023, 11, 11)

        val dated: MeasurementFormViewModel = koin.viewModel(saturday)
        val undated: MeasurementFormViewModel = koin.viewModel(null)

        assertEquals(saturday, dated.state.value?.day)
        assertEquals(gym.today, undated.state.value?.day)
    }

    @Test
    fun body_fat_is_shown_by_the_given_method() {
        val vm: BodyFatViewModel = koin.viewModel(BodyFatMethod.Ymca)

        assertEquals("YMCA", vm.state.value?.title)
    }

    @Test
    fun a_measure_screen_shows_the_given_measure() =
        runTest {
            val seeded = missingDefaults(null, emptySet())
            seeded.forEach { gym.measures.upsert(it) }
            val waist = seeded.first { it.kind == MeasureKind.Waist }

            val vm: MeasureViewModel = koin.viewModel(waist.id)

            assertEquals(waist.name, vm.state.value?.name)
        }

    @Test
    fun the_machine_picker_lists_the_given_day_s_machines_last_and_all_without_a_day() =
        runTest {
            gym.machines.upsert(press)
            val today = Visit(VisitId.random(), null, gym.today, t0, t0, false, listOf(press.id))
            gym.visits.upsert(today)

            val dated: MachinePickerViewModel = koin.viewModel(gym.today)
            val undated: MachinePickerViewModel = koin.viewModel(null)
            dated.load()
            undated.load()

            assertEquals(
                listOf(press.id),
                dated.state.value.inVisitRows
                    .map { it.id },
            )
            assertEquals(
                listOf(press.id),
                undated.state.value.rows
                    .map { it.id },
            )
        }

    @Test
    fun the_plan_form_opens_the_given_plan_or_starts_from_the_given_visit_or_machine() =
        runTest {
            gym.machines.upsert(press)
            val legs = Plan(PlanId.random(), null, "Ноги", listOf(press.id), t0, t0, false)
            gym.plans.upsert(legs)
            val seventh = CalendarDay(2023, 11, 7)
            val visit = Visit(VisitId.random(), null, seventh, t0, t0, false, listOf(press.id))
            gym.visits.upsert(visit)

            val editing: PlanFormViewModel = koin.viewModel(legs.id, null, null)
            val fromVisit: PlanFormViewModel = koin.viewModel(null, seventh, null)
            val fromMachine: PlanFormViewModel = koin.viewModel(null, null, press.id)
            val blank: PlanFormViewModel = koin.viewModel(null, null, null)

            assertEquals("Ноги", editing.state.value.name)
            assertEquals(
                listOf(press.id),
                fromVisit.state.value.rows
                    .map { it.id },
            )
            assertEquals(
                listOf(press.id),
                fromMachine.state.value.rows
                    .map { it.id },
            )
            assertEquals("" to emptyList(), blank.state.value.let { it.name to it.rows })
        }

    @Test
    fun statistics_open_on_the_given_machine_and_on_the_overview_without_one() =
        runTest {
            gym.machines.upsert(press)
            val set =
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    VisitId.random(),
                    press.id,
                    70.0,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                )
            gym.sets.upsert(set)

            val chosen: StatisticsViewModel = koin.viewModel(press.id)
            val overview: StatisticsViewModel = koin.viewModel(null)

            assertEquals(
                press.id,
                chosen.state.value.selected
                    ?.id,
            )
            assertNull(overview.state.value.selected)
        }

    @Test
    fun the_machine_form_starts_from_the_given_arguments() {
        val vm: MachineFormViewModel = koin.viewModel(MachineFormArgs(null, null, "Жим лёжа"))

        assertEquals("Жим лёжа", vm.state.value.name)
    }

    @Test
    fun the_link_chooser_searches_for_the_given_start_and_for_the_machine_s_name_without_one() =
        runTest {
            val signed = signedInGym()
            val mine = Machine.new("Жим ногами", ME.userId, t0)
            signed.machines.upsert(mine)
            val koin = koinOver(signed)

            val suggested: LinkChooserViewModel = koin.viewModel(mine.id, "Смит")
            val plain: LinkChooserViewModel = koin.viewModel(mine.id, null)
            suggested.load()
            plain.load()

            assertEquals("Смит", suggested.state.value.query)
            assertEquals("Жим ногами", plain.state.value.query)
        }

    @Test
    fun the_mine_chooser_searches_for_the_given_name() {
        val signed = signedInGym()
        signed.friends.group("Зал на Лесной", owner = OLEG, ME)
        val olegs = Machine.new("Платформа", OLEG.userId, t0)
        signed.friends.machines += olegs

        val vm: MineChooserViewModel = koinOver(signed).viewModel(olegs.id, "Платформа")
        vm.load()

        assertEquals("Платформа", vm.state.value.query)
    }

    @Test
    fun a_friend_s_machine_screen_shows_the_given_machine_of_the_given_friend() {
        val signed = signedInGym()
        signed.friends.group("Зал на Лесной", owner = OLEG, ME)
        val olegs = Machine.new("Платформа", OLEG.userId, t0)
        signed.friends.machines += olegs

        val vm: FriendMachineViewModel = koinOver(signed).viewModel(olegs.id, OLEG.userId)

        val state = assertNotNull(vm.state.value)
        assertEquals("Платформа" to "Олег", state.name to state.owner)
    }

    @Test
    fun a_friend_s_visit_shows_the_given_friend_on_the_given_day() =
        runTest {
            val signed = signedInGym()
            val fixture = OlegVisitFixture(signed)
            fixture.install()

            val vm: FriendVisitViewModel =
                koinOver(signed).viewModel(OLEG.userId, "Олег", fixture.yesterday)

            val state = assertNotNull(vm.state.value)
            assertEquals("Олег", state.title)
            assertEquals("Понедельник, 13 ноября", state.day)
        }

    @Test
    fun a_friend_s_calendar_marks_the_given_friend_s_visits() =
        runTest {
            val signed = signedInGym()
            val fixture = OlegVisitFixture(signed)
            fixture.install()

            val vm: FriendCalendarViewModel = koinOver(signed).viewModel(OLEG.userId)

            val marked =
                assertNotNull(vm.state.value)
                    .weeks
                    .flatten()
                    .filterNotNull()
                    .filter { it.friendDots.isNotEmpty() }
                    .map { it.day }
            assertEquals(listOf(fixture.yesterday), marked)
        }

    @Test
    fun a_group_screen_shows_the_given_group() {
        val signed = signedInGym()
        val group = signed.friends.group("Зал на Лесной", owner = OLEG, ME)

        val vm: GroupViewModel = koinOver(signed).viewModel(group.id)

        assertEquals("Зал на Лесной", vm.state.value?.title)
    }
}
