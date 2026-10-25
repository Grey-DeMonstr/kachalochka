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
import monster.greyde.kachalochka.core.domain.measures.Cycle
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.bands
import monster.greyde.kachalochka.core.domain.measures.bodyFat
import monster.greyde.kachalochka.core.domain.measures.cycleAnchors
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.components.HistoryRowUi
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import kotlin.math.round
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

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
            gym.deviceClock,
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
    fun a_woman_s_chart_shows_her_cycle_phases_up_to_three_months() {
        val cycleDay =
            missingDefaults(null, seeded.mapNotNull { it.kind }.toSet(), Sex.Female).single()
        val start = CalendarDay(2023, 11, 1)
        runBlocking {
            gym.profiles.upsert(profile.copy(sex = Sex.Female, updatedAt = t0 + 1.minutes))
            gym.measures.upsert(cycleDay)
            gym.measurements.upsert(
                Measurement(MeasurementId.random(), null, cycleDay.id, start, 1.0, t0, false),
            )
        }
        val cycle = Cycle(cycleAnchors(mapOf(start to 1.0)), 28)
        val vm = viewModel(BodyFatMethod.Ymca)

        assertEquals(CyclePhasesUi(october, today, cycle.bands(october, today)), vm.ui.phases)
        vm.choose(MeasurePeriod.Year)
        assertNull(vm.ui.phases)
        assertNull(viewModel(BodyFatMethod.Navy).ui.phases)
    }

    @Test
    fun the_chart_starts_on_the_first_day_with_every_measure_and_fills_the_gaps() {
        val ui = viewModel(BodyFatMethod.Navy).ui

        // The waist goes from 92 to 90 over 35 days; 28 of them have passed on 7 November.
        val before = navy(90.4)
        val latest = navy(90.0)
        assertEquals("ВМС США", ui.title)
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

        assertEquals("YMCA", ui.title)
        assertTrue(ui.points.isEmpty())
        assertNull(ui.latest)
        assertNull(ui.lastMeasured)
        assertTrue(ui.results.isEmpty())
        assertEquals("Нужно: пол, вес, талия", ui.required)
        assertTrue(ui.profileIncomplete)
    }

    @Test
    fun a_formula_with_its_inputs_still_names_them() {
        val ui = viewModel(BodyFatMethod.Navy).ui

        assertEquals("Нужно: пол, рост, талия, шея", ui.required)
        assertFalse(ui.profileIncomplete)
    }

    @Test
    fun a_missing_measure_does_not_ask_for_the_profile() {
        val waist = seeded.first { it.kind == MeasureKind.Waist }
        runBlocking {
            gym.measurements.rows.values
                .filter { it.measureId == waist.id }
                .forEach { gym.measurements.upsert(it.copy(deleted = true)) }
        }

        val ui = viewModel(BodyFatMethod.Ymca).ui

        assertNull(ui.latest)
        assertFalse(ui.profileIncomplete)
    }

    @Test
    fun an_account_switch_shows_the_new_account_s_results() {
        val vm = viewModel(BodyFatMethod.Deurenberg)
        val owner = IVAN_SESSION.account.userId
        val ivanWeight =
            missingDefaults(owner, emptySet()).first { it.kind == MeasureKind.Weight }
        runBlocking {
            gym.measures.upsert(ivanWeight)
            gym.profiles.upsert(
                Profile.new(owner, t0).copy(
                    sex = Sex.Male,
                    birthDate = CalendarDay(1987, 1, 1),
                    heightCm = 180.0,
                ),
            )
            gym.measurements.upsert(
                Measurement(MeasurementId.random(), owner, ivanWeight.id, today, 70.0, t0, false),
            )
        }

        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)

        assertEquals(listOf(today to deurenberg(70.0)), vm.ui.points)
    }
}
