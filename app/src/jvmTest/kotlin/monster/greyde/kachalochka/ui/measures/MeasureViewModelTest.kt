package monster.greyde.kachalochka.ui.measures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val seeded = missingDefaults(null, emptySet())
    private val weight = seeded.first { it.kind == MeasureKind.Weight }
    private val waist = seeded.first { it.kind == MeasureKind.Waist }

    // Today is 14 November 2023: a quarter reaches back to 14 August, a month to 14 October.
    private val weighed =
        listOf(
            CalendarDay(2022, 12, 1) to 86.0,
            CalendarDay(2023, 5, 10) to 85.0,
            CalendarDay(2023, 8, 1) to 84.0,
            CalendarDay(2023, 10, 10) to 83.0,
            CalendarDay(2023, 11, 7) to 82.4,
            CalendarDay(2023, 11, 14) to 82.0,
        ).map { (day, value) -> record(weight, day, value) }
    private val waisted = record(waist, CalendarDay(2023, 11, 14), 90.0)
    private val ivan =
        AccountSession(
            Account(UserId("11111111-1111-4111-8111-111111111111"), "ivan@example.test", "Иван"),
            "access",
            "refresh",
            t0,
        )

    init {
        runBlocking { seeded.forEach { gym.measures.upsert(it) } }
    }

    private fun record(
        measure: Measure,
        day: CalendarDay,
        value: Double,
    ): Measurement =
        Measurement(MeasurementId.random(), null, measure.id, day, value, t0, false).also {
            runBlocking { gym.measurements.upsert(it) }
        }

    private fun viewModel() =
        MeasureViewModel(
            weight.id,
            gym.measures,
            gym.measurements,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.utcOffset,
            gym.sync,
        )

    private val MeasureViewModel.ui: MeasureUi get() = state.value!!

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_measure_opens_on_the_last_three_months() {
        val ui = viewModel().ui

        assertEquals("Вес", ui.name)
        assertEquals("кг", ui.unit)
        assertEquals(MeasurePeriod.Quarter, ui.period)
        assertEquals(
            listOf(
                CalendarDay(2023, 10, 10) to 83.0,
                CalendarDay(2023, 11, 7) to 82.4,
                CalendarDay(2023, 11, 14) to 82.0,
            ),
            ui.points,
        )
        assertEquals("82 кг", ui.latest)
        assertEquals("−1", ui.change)
        assertNull(ui.editing)
        assertFalse(ui.deleting)
    }

    @Test
    fun another_period_changes_the_points_and_the_change() {
        val vm = viewModel()

        vm.choose(MeasurePeriod.Month)
        assertEquals(2, vm.ui.points.size)
        assertEquals("−0,4", vm.ui.change)

        vm.choose(MeasurePeriod.HalfYear)
        assertEquals(4, vm.ui.points.size)
        assertEquals("−2", vm.ui.change)

        vm.choose(MeasurePeriod.All)
        assertEquals(6, vm.ui.points.size)
        assertEquals("−4", vm.ui.change)
        assertEquals("82 кг", vm.ui.latest)
    }

    @Test
    fun a_period_with_one_value_shows_no_change() {
        runBlocking {
            gym.measurements.upsert(weighed.last().copy(deleted = true, updatedAt = t0))
        }
        val vm = viewModel()

        vm.choose(MeasurePeriod.Month)

        assertEquals(listOf(CalendarDay(2023, 11, 7) to 82.4), vm.ui.points)
        assertEquals("82,4 кг", vm.ui.latest)
        assertNull(vm.ui.change)
    }

    @Test
    fun the_history_lists_every_value_newest_first() {
        val history = viewModel().ui.history

        assertEquals(
            listOf(
                HistoryRowUi(CalendarDay(2023, 11, 14), "14 ноября", "82 кг"),
                HistoryRowUi(CalendarDay(2023, 11, 7), "7 ноября", "82,4 кг"),
                HistoryRowUi(CalendarDay(2023, 10, 10), "10 октября", "83 кг"),
                HistoryRowUi(CalendarDay(2023, 8, 1), "1 августа", "84 кг"),
                HistoryRowUi(CalendarDay(2023, 5, 10), "10 мая", "85 кг"),
                HistoryRowUi(CalendarDay(2022, 12, 1), "1 декабря 2022", "86 кг"),
            ),
            history,
        )
    }

    @Test
    fun editing_renames_the_measure_and_changes_its_unit() {
        val vm = viewModel()
        gym.clock.current = t0 + 5.minutes

        vm.openEdit()
        assertEquals(EditMeasureUi("Вес", "кг", true), vm.ui.editing)
        vm.typeName("  ")
        assertFalse(vm.ui.editing!!.canSave)
        vm.confirmEdit()
        assertEquals(
            "Вес",
            gym.measures.rows
                .getValue(weight.id)
                .name,
        )

        vm.typeName("Масса тела ")
        vm.typeUnit("lb")
        vm.confirmEdit()

        assertEquals(
            weight.copy(name = "Масса тела", unit = "lb", updatedAt = t0 + 5.minutes),
            gym.measures.rows.getValue(weight.id),
        )
        assertNull(vm.ui.editing)
        assertEquals("Масса тела", vm.ui.name)
        assertEquals("82 lb", vm.ui.latest)
    }

    @Test
    fun a_dismissed_edit_writes_nothing() {
        val vm = viewModel()

        vm.openEdit()
        vm.typeName("Масса")
        vm.dismissEdit()

        assertNull(vm.ui.editing)
        assertEquals(weight, gym.measures.rows.getValue(weight.id))
    }

    @Test
    fun the_measure_is_gone_for_an_account_that_does_not_have_it() {
        val vm = viewModel()
        assertFalse(vm.gone.value)

        gym.withAccounts(ivan, active = ivan)

        assertTrue(vm.gone.value)
    }

    @Test
    fun deleting_removes_the_measure_and_its_values_after_confirmation() {
        val vm = viewModel()
        gym.clock.current = t0 + 5.minutes

        vm.askDelete()
        assertTrue(vm.ui.deleting)
        vm.cancelDelete()
        assertFalse(vm.ui.deleting)
        assertEquals(weight, gym.measures.rows.getValue(weight.id))
        assertFalse(vm.gone.value)

        vm.askDelete()
        vm.confirmDelete()

        assertTrue(vm.gone.value)
        assertEquals(
            weight.copy(deleted = true, updatedAt = t0 + 5.minutes),
            gym.measures.rows.getValue(weight.id),
        )
        weighed.forEach {
            assertEquals(
                it.copy(deleted = true, updatedAt = t0 + 5.minutes),
                gym.measurements.rows.getValue(it.id),
            )
        }
        assertEquals(listOf(waisted), runBlocking { gym.measurements.all(null) })
    }
}
