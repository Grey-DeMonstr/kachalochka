package monster.greyde.kachalochka.ui.measures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.calendar.DayUi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementFormViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val today = gym.today
    private val saturday = CalendarDay(2023, 11, 11)
    private val seeded = missingDefaults(null, emptySet())
    private val weight = seeded.of(MeasureKind.Weight)
    private val waist = seeded.of(MeasureKind.Waist)
    private val chest = seeded.of(MeasureKind.Chest)
    private val neck = seeded.of(MeasureKind.Neck)

    private fun List<Measure>.of(kind: MeasureKind) = first { it.kind == kind }

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

    private val ivan =
        AccountSession(
            Account(UserId("11111111-1111-4111-8111-111111111111"), "ivan@example.test", "Иван"),
            "access",
            "refresh",
            t0,
        )

    private fun viewModel(
        day: CalendarDay? = null,
        on: FakeGym = gym,
    ) = MeasurementFormViewModel(
        day,
        on.measures,
        on.measurements,
        on.currentUser,
        on.accounts,
        on.clock,
        on.utcOffset,
        on.sync,
    )

    private fun MeasurementFormViewModel.field(measure: Measure) =
        state.value!!.fields.first { it.id == measure.id }

    private fun MeasurementFormViewModel.pickerDay(day: CalendarDay): DayUi =
        state.value!!
            .picker!!
            .weeks
            .flatten()
            .filterNotNull()
            .first { it.day == day }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_form_opens_on_today_with_one_empty_field_per_measure() {
        val vm = viewModel()

        val form = vm.state.value!!
        assertEquals(today, form.day)
        assertEquals("Сегодня, 14 ноября", form.dayTitle)
        assertEquals(seeded.map { it.name }, form.fields.map { it.name })
        assertEquals(
            MeasureFieldUi(
                weight.id,
                "Вес",
                "кг",
                "",
                null,
                true,
                howToMeasure(MeasureKind.Weight),
            ),
            form.fields.first(),
        )
        assertFalse(form.canSave)
        assertFalse(form.canDelete)
        assertFalse(form.picking)
        assertFalse(form.deleting)
    }

    @Test
    fun a_day_with_values_loads_them_for_editing() {
        record(weight, saturday, 82.4)
        record(waist, saturday, 90.0)

        val vm = viewModel(saturday)

        assertEquals("Суббота, 11 ноября", vm.state.value!!.dayTitle)
        assertEquals("82,4", vm.field(weight).text)
        assertEquals("90", vm.field(waist).text)
        assertEquals("", vm.field(chest).text)
        assertTrue(vm.state.value!!.canDelete)
        assertFalse(vm.state.value!!.canSave)
    }

    @Test
    fun a_hint_is_the_latest_value_before_the_day() {
        record(weight, today.plusDays(-14), 83.0)
        record(weight, today.plusDays(-7), 82.5)
        record(weight, today, 82.0)

        assertEquals("82,5", viewModel(saturday).field(weight).hint)
        assertNull(viewModel(today.plusDays(-14)).field(weight).hint)
        assertNull(viewModel(saturday).field(waist).hint)
    }

    @Test
    fun a_decimal_is_valid_and_anything_else_disables_saving() {
        val vm = viewModel()

        vm.type(weight.id, "82,4")
        assertTrue(vm.field(weight).valid)
        assertTrue(vm.state.value!!.canSave)

        vm.type(waist.id, "abc")
        assertFalse(vm.field(waist).valid)
        assertFalse(vm.state.value!!.canSave)
    }

    @Test
    fun saving_writes_changed_values_clears_emptied_ones_and_syncs_a_past_day() =
        runTest {
            val weighed = record(weight, saturday, 82.4)
            val waisted = record(waist, saturday, 90.0)
            val chested = record(chest, saturday, 100.0)
            val vm = viewModel(saturday)
            gym.clock.current = t0 + 5.minutes
            var saved = 0

            vm.type(weight.id, "82")
            vm.type(waist.id, "")
            vm.type(neck.id, "38")
            vm.type(chest.id, "100,0")
            vm.save { saved++ }

            assertEquals(1, saved)
            val rows = gym.measurements.rows
            assertEquals(weighed.copy(value = 82.0, updatedAt = t0 + 5.minutes), rows[weighed.id])
            assertEquals(
                waisted.copy(deleted = true, updatedAt = t0 + 5.minutes),
                rows[waisted.id],
            )
            assertEquals(chested, rows[chested.id])
            val neckRow = rows.values.single { it.measureId == neck.id }
            assertEquals(
                Triple(saturday, 38.0, false),
                Triple(neckRow.day, neckRow.value, neckRow.deleted),
            )
            assertEquals(
                mapOf(weight.id to 82.0, chest.id to 100.0, neck.id to 38.0),
                gym.measurements.all(null).associate { it.measureId to it.value },
            )
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun saving_today_leaves_the_sync_to_its_schedule() {
        val vm = viewModel()

        vm.type(weight.id, "82")
        vm.save {}

        assertEquals(listOf(82.0), runBlocking { gym.measurements.all(null) }.map { it.value })
        assertEquals(0, gym.sync.requests)
    }

    @Test
    fun choosing_another_day_loads_it_and_drops_what_was_typed() {
        record(weight, saturday, 82.4)
        val vm = viewModel()
        vm.type(waist.id, "91")

        vm.pickDay()
        assertTrue(vm.state.value!!.picking)
        assertEquals(
            "Ноябрь 2023",
            vm.state.value!!
                .picker!!
                .monthTitle,
        )
        vm.chooseDay(saturday)

        val form = vm.state.value!!
        assertEquals(saturday, form.day)
        assertFalse(form.picking)
        assertEquals("82,4", vm.field(weight).text)
        assertEquals("", vm.field(waist).text)
    }

    @Test
    fun the_picker_marks_days_with_values_and_keeps_the_future_out_of_reach() {
        record(weight, saturday, 82.4)
        val vm = viewModel()
        vm.pickDay()

        assertTrue(vm.pickerDay(saturday).hasVisit)
        assertFalse(vm.pickerDay(today.plusDays(-1)).hasVisit)
        assertFalse(vm.pickerDay(today.plusDays(1)).enabled)
        assertFalse(
            vm.state.value!!
                .picker!!
                .canShowNextMonth,
        )

        vm.chooseDay(today.plusDays(1))
        assertEquals(today, vm.state.value!!.day)

        vm.showMonth(-1)
        assertEquals(
            "Октябрь 2023",
            vm.state.value!!
                .picker!!
                .monthTitle,
        )
        vm.showMonth(+1)
        vm.showMonth(+1)
        assertEquals(
            "Ноябрь 2023",
            vm.state.value!!
                .picker!!
                .monthTitle,
        )

        vm.dismissPick()
        assertFalse(vm.state.value!!.picking)
    }

    @Test
    fun deleting_the_measurement_clears_every_value_of_the_day_after_confirmation() {
        val weighed = record(weight, saturday, 82.4)
        val waisted = record(waist, saturday, 90.0)
        val earlier = record(weight, saturday.plusDays(-7), 83.0)
        val vm = viewModel(saturday)
        var deleted = 0

        vm.askDelete()
        assertTrue(vm.state.value!!.deleting)
        vm.cancelDelete()
        assertFalse(vm.state.value!!.deleting)
        assertEquals(3, runBlocking { gym.measurements.all(null) }.size)

        vm.askDelete()
        vm.confirmDelete { deleted++ }

        assertEquals(1, deleted)
        assertTrue(
            gym.measurements.rows
                .getValue(weighed.id)
                .deleted,
        )
        assertTrue(
            gym.measurements.rows
                .getValue(waisted.id)
                .deleted,
        )
        assertEquals(listOf(earlier), runBlocking { gym.measurements.all(null) })
        assertEquals(1, gym.sync.requests)
    }

    @Test
    fun a_day_without_values_offers_no_deletion() {
        val vm = viewModel()

        vm.askDelete()

        assertFalse(vm.state.value!!.canDelete)
        assertFalse(vm.state.value!!.deleting)
    }

    @Test
    fun a_predefined_field_says_how_to_measure_and_shows_the_app_s_name() {
        val forearm = Measure(MeasureId.random(), null, "Предплечье", "см", null, 7, t0, false)
        runBlocking {
            gym.measures.upsert(forearm)
            gym.measures.upsert(waist.copy(name = "Пояс", unit = "дюйм"))
        }

        val vm = viewModel()

        assertEquals("Талия" to "см", vm.field(waist).let { it.name to it.unit })
        assertTrue(
            vm
                .field(waist)
                .howTo!!
                .contains("пупка"),
        )
        assertNull(vm.field(forearm).howTo)
    }
}
