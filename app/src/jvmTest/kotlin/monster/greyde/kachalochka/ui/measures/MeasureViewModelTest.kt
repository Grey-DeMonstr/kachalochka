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
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
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
    private val forearm = Measure(MeasureId.random(), null, "Предплечье", "см", null, 7, t0, false)
    private val forearmed =
        listOf(CalendarDay(2023, 11, 1) to 30.0, CalendarDay(2023, 11, 14) to 31.0)
            .map { (day, value) -> record(forearm, day, value) }
    private val ivan =
        AccountSession(
            Account(UserId("11111111-1111-4111-8111-111111111111"), "ivan@example.test", "Иван"),
            "access",
            "refresh",
            t0,
        )

    init {
        runBlocking { (seeded + forearm).forEach { gym.measures.upsert(it) } }
    }

    private fun record(
        measure: Measure,
        day: CalendarDay,
        value: Double,
    ): Measurement =
        Measurement(MeasurementId.random(), null, measure.id, day, value, t0, false).also {
            runBlocking { gym.measurements.upsert(it) }
        }

    private fun viewModel(id: MeasureId = weight.id) =
        MeasureViewModel(
            id,
            gym.measures,
            gym.measurements,
            gym.profiles,
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
        assertEquals("−0.4", vm.ui.change)

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
        assertEquals("82.4 кг", vm.ui.latest)
        assertNull(vm.ui.change)
    }

    private val MeasureHistoryUi.marked: List<CalendarDay>
        get() =
            weeks
                .flatten()
                .filterNotNull()
                .filter { it.hasVisit }
                .map { it.day }

    private fun stored(
        measure: Measure,
        day: CalendarDay,
    ): Measurement? =
        gym.measurements.rows.values.singleOrNull {
            it.measureId == measure.id && it.day == day && !it.deleted
        }

    @Test
    fun the_measure_opens_on_today_s_value() {
        val ui = viewModel().ui

        assertEquals("Вес", ui.title)
        assertEquals(
            MeasureEntryUi(
                CalendarDay(2023, 11, 14),
                "Сегодня, 14 ноября",
                "82",
                canSave = false,
                canDelete = true,
            ),
            ui.entry,
        )
        assertNull(ui.history)
    }

    @Test
    fun a_typed_value_replaces_today_s() {
        val today = CalendarDay(2023, 11, 14)
        val vm = viewModel(forearm.id)
        val before = stored(forearm, today)!!

        vm.typeValue("31,5")
        assertTrue(vm.ui.entry.canSave)
        vm.saveValue()

        val after = stored(forearm, today)!!
        assertEquals(before.id, after.id)
        assertEquals(31.5, after.value)
        assertEquals("31.5", vm.ui.entry.text)
        assertFalse(vm.ui.entry.canSave)
        assertEquals("31.5 см", vm.ui.latest)
        assertEquals(0, gym.sync.requests)
    }

    @Test
    fun a_value_that_is_not_a_number_cannot_be_saved() {
        val vm = viewModel()

        vm.typeValue("8x")
        assertFalse(vm.ui.entry.canSave)
        vm.typeValue("")
        assertFalse(vm.ui.entry.canSave)
    }

    @Test
    fun an_empty_day_steps_by_half_from_the_latest_value() {
        gym.clock.current = t0 + 1.days
        val vm = viewModel()
        assertEquals(
            MeasureEntryUi(
                CalendarDay(2023, 11, 15),
                "Сегодня, 15 ноября",
                "",
                canSave = false,
                canDelete = false,
            ),
            vm.ui.entry,
        )

        vm.stepValue(+1)
        assertEquals("82.5", vm.ui.entry.text)
        vm.stepValue(-1)
        vm.stepValue(-1)
        assertEquals("81.5", vm.ui.entry.text)
        vm.saveValue()

        assertEquals(81.5, stored(weight, CalendarDay(2023, 11, 15))?.value)
        assertTrue(vm.ui.entry.canDelete)
    }

    @Test
    fun the_history_marks_the_month_s_days_with_a_value() {
        val vm = viewModel()

        vm.openHistory()

        assertEquals("Вес · история", vm.ui.title)
        val history = vm.ui.history!!
        assertEquals("Ноябрь 2023", history.monthTitle)
        assertFalse(history.canShowNextMonth)
        assertEquals(listOf(CalendarDay(2023, 11, 7), CalendarDay(2023, 11, 14)), history.marked)

        vm.showHistoryMonth(-1)

        assertEquals("Октябрь 2023", vm.ui.history?.monthTitle)
        assertEquals(listOf(CalendarDay(2023, 10, 10)), vm.ui.history?.marked)
    }

    @Test
    fun a_day_in_the_history_shows_its_value_and_deletes_it() {
        val day = CalendarDay(2023, 11, 7)
        val vm = viewModel().also { it.openHistory() }

        vm.chooseDay(day)
        assertEquals(
            MeasureEntryUi(day, "Вторник, 7 ноября", "82.4", canSave = false, canDelete = true),
            vm.ui.entry,
        )
        vm.deleteValue()

        assertNull(stored(weight, day))
        assertEquals("", vm.ui.entry.text)
        assertFalse(vm.ui.entry.canDelete)
        assertEquals(listOf(CalendarDay(2023, 11, 14)), vm.ui.history?.marked)
        assertEquals(1, gym.sync.requests)
    }

    @Test
    fun an_empty_day_in_the_history_takes_a_new_value() {
        val day = CalendarDay(2023, 11, 1)
        val vm = viewModel().also { it.openHistory() }

        vm.chooseDay(day)
        vm.typeValue("82.8")
        vm.saveValue()

        assertEquals(82.8, stored(weight, day)?.value)
        assertEquals(1, gym.sync.requests)
    }

    @Test
    fun a_day_after_today_cannot_be_chosen() {
        val vm = viewModel().also { it.openHistory() }

        vm.chooseDay(CalendarDay(2023, 11, 15))

        assertEquals(CalendarDay(2023, 11, 14), vm.ui.entry.day)
    }

    @Test
    fun closing_the_history_returns_to_today() {
        val vm = viewModel().also { it.openHistory() }
        vm.chooseDay(CalendarDay(2023, 11, 7))

        assertTrue(vm.closeHistory())

        assertNull(vm.ui.history)
        assertEquals("Вес", vm.ui.title)
        assertEquals(CalendarDay(2023, 11, 14), vm.ui.entry.day)
        assertFalse(vm.closeHistory())
    }

    @Test
    fun editing_renames_the_user_s_own_measure_and_changes_its_unit() {
        val vm = viewModel(forearm.id)
        gym.clock.current = t0 + 5.minutes

        assertTrue(vm.ui.canEdit)
        vm.openEdit()
        assertEquals(EditMeasureUi("Предплечье", "см", true), vm.ui.editing)
        vm.typeName("  ")
        assertFalse(vm.ui.editing!!.canSave)
        vm.confirmEdit()
        assertEquals(
            "Предплечье",
            gym.measures.rows
                .getValue(forearm.id)
                .name,
        )

        vm.typeName("Запястье ")
        vm.typeUnit("дюйм")
        vm.confirmEdit()

        assertEquals(
            forearm.copy(name = "Запястье", unit = "дюйм", updatedAt = t0 + 5.minutes),
            gym.measures.rows.getValue(forearm.id),
        )
        assertNull(vm.ui.editing)
        assertEquals("Запястье", vm.ui.name)
        assertEquals("31 дюйм", vm.ui.latest)
        assertEquals(1, gym.sync.requests)
    }

    @Test
    fun a_dismissed_edit_writes_nothing() {
        val vm = viewModel(forearm.id)

        vm.openEdit()
        vm.typeName("Запястье")
        vm.dismissEdit()

        assertNull(vm.ui.editing)
        assertEquals(forearm, gym.measures.rows.getValue(forearm.id))
    }

    @Test
    fun a_predefined_measure_shows_the_app_s_name_and_cannot_be_renamed() {
        runBlocking { gym.measures.upsert(weight.copy(name = "Масса", unit = "lb")) }
        val vm = viewModel()

        assertEquals("Вес" to "кг", vm.ui.name to vm.ui.unit)
        assertFalse(vm.ui.canEdit)
        vm.openEdit()
        assertNull(vm.ui.editing)
    }

    @Test
    fun a_measure_a_formula_reads_cannot_be_deleted_and_an_unread_one_can() {
        val locked = viewModel()
        val chest = viewModel(seeded.first { it.kind == MeasureKind.Chest }.id)

        assertFalse(locked.ui.canDelete)
        locked.askDelete()
        assertFalse(locked.ui.deleting)
        assertTrue(chest.ui.canDelete)
        assertTrue(viewModel(forearm.id).ui.canDelete)
    }

    @Test
    fun hips_can_be_deleted_by_a_man_only() {
        val hips = seeded.first { it.kind == MeasureKind.Hips }.id
        assertFalse(viewModel(hips).ui.canDelete)

        runBlocking {
            gym.profiles.upsert(Profile.new(null, t0).copy(sex = Sex.Male))
        }

        assertTrue(viewModel(hips).ui.canDelete)
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
        val vm = viewModel(forearm.id)
        gym.clock.current = t0 + 5.minutes

        vm.askDelete()
        assertTrue(vm.ui.deleting)
        vm.cancelDelete()
        assertFalse(vm.ui.deleting)
        assertEquals(forearm, gym.measures.rows.getValue(forearm.id))
        assertFalse(vm.gone.value)
        assertEquals(0, gym.sync.requests)

        vm.askDelete()
        vm.confirmDelete()

        assertTrue(vm.gone.value)
        assertEquals(
            forearm.copy(deleted = true, updatedAt = t0 + 5.minutes),
            gym.measures.rows.getValue(forearm.id),
        )
        forearmed.forEach {
            assertEquals(
                it.copy(deleted = true, updatedAt = t0 + 5.minutes),
                gym.measurements.rows.getValue(it.id),
            )
        }
        assertEquals(
            (weighed + waisted).toSet(),
            runBlocking { gym.measurements.all(null) }.toSet(),
        )
        assertEquals(1, gym.sync.requests)
    }
}
