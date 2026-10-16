package monster.greyde.kachalochka.ui.measures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.BodyInputs
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.bodyFat
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.components.HistoryRowUi
import kotlin.math.round
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BodyFatViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val seeded = missingDefaults(null, emptySet())

    // Today is 14 November 2023: a quarter reaches back to 14 August.
    private val today = CalendarDay(2023, 11, 14)
    private val lastWeek = CalendarDay(2023, 11, 7)
    private val october = CalendarDay(2023, 10, 10)
    private val may = CalendarDay(2023, 5, 10)

    private val profile =
        Profile.new(null, t0).copy(
            sex = Sex.Male,
            birthDate = CalendarDay(1987, 1, 1),
            heightCm = 180.0,
        )

    init {
        runBlocking {
            seeded.forEach { gym.measures.upsert(it) }
            gym.profiles.upsert(profile)
        }
        record(MeasureKind.Weight, may, 90.0)
        record(MeasureKind.Weight, october, 86.0)
        record(MeasureKind.Weight, today, 85.0)
        record(MeasureKind.Waist, october, 92.0)
        record(MeasureKind.Waist, today, 90.0)
        record(MeasureKind.Neck, lastWeek, 40.0)
    }

    private fun record(
        kind: MeasureKind,
        day: CalendarDay,
        value: Double,
    ) = runBlocking {
        val measure = seeded.first { it.kind == kind }
        gym.measurements.upsert(
            Measurement(MeasurementId.random(), null, measure.id, day, value, t0, false),
        )
    }

    private fun viewModel(method: BodyFatMethod) =
        BodyFatViewModel(
            method,
            gym.measures,
            gym.measurements,
            gym.profiles,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.utcOffset,
            gym.sync,
        )

    private val BodyFatViewModel.ui: BodyFatUi get() = state.value!!

    private fun navy(waist: Double) =
        tenth(
            bodyFat(
                BodyFatMethod.Navy,
                BodyInputs(Sex.Male, 36, 180.0, null, waist, 40.0, null),
            )!!,
        )

    private fun deurenberg(weight: Double) =
        tenth(
            bodyFat(
                BodyFatMethod.Deurenberg,
                BodyInputs(Sex.Male, 36, 180.0, weight, null, null, null),
            )!!,
        )

    private fun tenth(value: Double) = round(value * 10) / 10

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_chart_starts_on_the_first_day_with_every_measure_and_fills_the_gaps() {
        val ui = viewModel(BodyFatMethod.Navy).ui

        // The waist goes from 92 to 90 over 35 days; 28 of them have passed on 7 November.
        val before = navy(90.4)
        val latest = navy(90.0)
        assertEquals("Процент жира · ВМС США", ui.title)
        assertEquals(MeasurePeriod.Quarter, ui.period)
        assertEquals(listOf(lastWeek to before, today to latest), ui.points)
        assertEquals("${oneDecimal(latest)} %", ui.latest)
        assertEquals(measureDelta(latest, before), ui.change)
        assertEquals("Последний замер: сегодня", ui.lastMeasured)
        assertEquals(
            listOf(
                HistoryRowUi(today, "14 ноября", "${oneDecimal(latest)} %"),
                HistoryRowUi(lastWeek, "7 ноября", "${oneDecimal(before)} %"),
            ),
            ui.results,
        )
    }

    @Test
    fun another_period_changes_the_points_and_the_change() {
        val vm = viewModel(BodyFatMethod.Deurenberg)
        assertEquals(listOf(october, today), vm.ui.points.map { it.first })

        vm.choose(MeasurePeriod.All)

        assertEquals(
            listOf(
                may to deurenberg(90.0),
                october to deurenberg(86.0),
                today to deurenberg(85.0),
            ),
            vm.ui.points,
        )
        assertEquals(measureDelta(deurenberg(85.0), deurenberg(90.0)), vm.ui.change)
        assertEquals(3, vm.ui.results.size)
    }

    @Test
    fun a_period_with_one_point_shows_no_change() {
        val vm = viewModel(BodyFatMethod.Deurenberg)

        vm.choose(MeasurePeriod.Month)

        assertEquals(listOf(today to deurenberg(85.0)), vm.ui.points)
        assertNull(vm.ui.change)
    }

    @Test
    fun a_formula_without_its_inputs_has_nothing_to_show() {
        runBlocking { gym.profiles.upsert(profile.copy(sex = null)) }

        val ui = viewModel(BodyFatMethod.Ymca).ui

        assertEquals("Процент жира · YMCA", ui.title)
        assertTrue(ui.points.isEmpty())
        assertNull(ui.latest)
        assertNull(ui.lastMeasured)
        assertTrue(ui.results.isEmpty())
    }
}
