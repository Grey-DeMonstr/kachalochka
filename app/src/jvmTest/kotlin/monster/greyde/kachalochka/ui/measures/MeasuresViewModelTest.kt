package monster.greyde.kachalochka.ui.measures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.BodyInputs
import monster.greyde.kachalochka.core.domain.measures.MEASURE_SEEDED_AT
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.bodyFat
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
import kotlin.time.Duration.Companion.minutes

private class CountingMeasures(
    private val inner: MeasureRepository,
) : MeasureRepository by inner {
    var writes = 0

    override suspend fun upsert(measure: Measure) {
        writes++
        inner.upsert(measure)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MeasuresViewModelTest {
    private val gym = FakeGym()
    private val counting = CountingMeasures(gym.measures)
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
    private val defaults =
        listOf("Вес", "Талия", "Грудь", "Обхват бёдер", "Бицепс", "Окружность бедра", "Шея")

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    private fun viewModel(on: FakeGym = gym) =
        MeasuresViewModel(
            if (on === gym) counting else on.measures,
            on.measurements,
            on.profiles,
            on.currentUser,
            on.accounts,
            on.clock,
            on.utcOffset,
            on.sync,
        )

    private suspend fun measureOf(kind: MeasureKind): Measure =
        gym.measures.all(null).first { it.kind == kind }

    private suspend fun record(
        measure: Measure,
        day: CalendarDay,
        value: Double,
    ) = gym.measurements.upsert(
        Measurement(MeasurementId.random(), null, measure.id, day, value, t0, false),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opening_seeds_the_seven_predefined_measures_once() =
        runTest {
            val vm = viewModel().also { it.load() }

            assertEquals(
                defaults,
                vm.state.value.rows
                    .map { it.name },
            )
            assertEquals(7, counting.writes)

            viewModel().load()

            assertEquals(7, counting.writes)
        }

    @Test
    fun a_deleted_predefined_measure_is_not_seeded_again() =
        runTest {
            val chest = missingDefaults(null, emptySet()).first { it.kind == MeasureKind.Chest }
            gym.measures.upsert(chest.copy(deleted = true))

            val vm = viewModel().also { it.load() }

            assertEquals(
                defaults - "Грудь",
                vm.state.value.rows
                    .map { it.name },
            )
        }

    @Test
    fun a_row_shows_the_latest_value_its_change_how_long_ago_and_the_formulas_reading_it() =
        runTest {
            viewModel().load()
            val weight = measureOf(MeasureKind.Weight)
            val chest = measureOf(MeasureKind.Chest)
            record(weight, gym.today.plusDays(-9), 83.0)
            record(weight, gym.today.plusDays(-7), 82.4)
            record(weight, gym.today.plusDays(-2), 82.0)
            record(chest, gym.today, 101.0)

            val vm = viewModel().also { it.load() }

            val rows =
                vm.state.value.rows
                    .associateBy { it.id }
            assertEquals(
                MeasureRowUi(
                    weight.id,
                    "Вес",
                    "82 кг",
                    "−0,4",
                    "2 дня назад",
                    listOf("YMCA", "BMI"),
                ),
                rows[weight.id],
            )
            assertEquals(
                MeasureRowUi(chest.id, "Грудь", "101 см", null, "сегодня", emptyList()),
                rows[chest.id],
            )
            val waist = measureOf(MeasureKind.Waist)
            assertEquals(
                MeasureRowUi(waist.id, "Талия", null, null, null, listOf("NAVY", "YMCA")),
                rows[waist.id],
            )
        }

    @Test
    fun a_predefined_measure_shows_the_app_s_name_whatever_is_stored() =
        runTest {
            viewModel().load()
            gym.measures.upsert(measureOf(MeasureKind.Hips).copy(name = "Бёдра"))

            val vm = viewModel().also { it.load() }

            assertEquals(
                defaults,
                vm.state.value.rows
                    .map { it.name },
            )
        }

    @Test
    fun a_deleted_measure_a_formula_reads_comes_back_without_its_values() =
        runTest {
            viewModel().load()
            val neck = measureOf(MeasureKind.Neck)
            gym.measures.upsert(neck.copy(deleted = true, updatedAt = t0))
            gym.clock.current = t0 + 5.minutes

            val vm = viewModel().also { it.load() }

            assertEquals(
                defaults,
                vm.state.value.rows
                    .map { it.name },
            )
            assertEquals(
                neck.copy(updatedAt = t0 + 5.minutes),
                gym.measures.rows.getValue(neck.id),
            )
        }

    @Test
    fun the_fat_measure_goes_with_its_values() =
        runTest {
            val fat =
                Measure(MeasureId.random(), null, "Жир", "%", MeasureKind.BodyFat, 7, t0, false)
            gym.measures.upsert(fat)
            record(fat, gym.today, 18.0)
            gym.clock.current = t0 + 5.minutes

            val vm = viewModel().also { it.load() }

            assertEquals(
                defaults,
                vm.state.value.rows
                    .map { it.name },
            )
            assertEquals(
                fat.copy(deleted = true, updatedAt = t0 + 5.minutes),
                gym.measures.rows.getValue(fat.id),
            )
            assertTrue(gym.measurements.all(null).none { it.measureId == fat.id })
        }

    @Test
    fun body_fat_is_calculated_from_the_latest_values_and_the_profile() =
        runTest {
            viewModel().load()
            gym.profiles.upsert(
                Profile.new(null, t0).copy(
                    sex = Sex.Male,
                    birthDate = CalendarDay(gym.today.year - 36, 1, 1),
                    heightCm = 180.0,
                ),
            )
            record(measureOf(MeasureKind.Weight), gym.today.plusDays(-30), 90.0)
            record(measureOf(MeasureKind.Weight), gym.today, 85.0)
            record(measureOf(MeasureKind.Waist), gym.today, 90.0)
            record(measureOf(MeasureKind.Neck), gym.today, 40.0)

            val vm = viewModel().also { it.load() }

            val inputs = BodyInputs(Sex.Male, 36, 180.0, 85.0, 90.0, 40.0, null)
            assertEquals(
                listOf(
                    FatRowUi("NAVY", "ВМС США", percent(BodyFatMethod.Navy, inputs), null),
                    FatRowUi("YMCA", "YMCA", percent(BodyFatMethod.Ymca, inputs), null),
                    FatRowUi("BMI", "Дойренберг", percent(BodyFatMethod.Deurenberg, inputs), null),
                ),
                vm.state.value.fat,
            )
            assertFalse(vm.state.value.profileIncomplete)
        }

    @Test
    fun without_a_profile_the_rows_name_what_they_lack() =
        runTest {
            viewModel().load()
            record(measureOf(MeasureKind.Weight), gym.today, 85.0)

            val vm = viewModel().also { it.load() }

            assertEquals(
                listOf(
                    FatRowUi("NAVY", "ВМС США", null, "Нужно: пол, рост, талия, шея"),
                    FatRowUi("YMCA", "YMCA", null, "Нужно: пол, талия"),
                    FatRowUi("BMI", "Дойренберг", null, "Нужно: пол, дата рождения, рост"),
                ),
                vm.state.value.fat,
            )
            assertTrue(vm.state.value.profileIncomplete)
        }

    private fun percent(
        method: BodyFatMethod,
        inputs: BodyInputs,
    ) = "${oneDecimal(bodyFat(method, inputs)!!)} %"

    @Test
    fun a_new_measure_is_added_at_the_end() =
        runTest {
            val vm = viewModel().also { it.load() }

            vm.openAdd()
            assertEquals(NewMeasureUi("", "", canSave = false), vm.state.value.adding)
            vm.typeName("Предплечье ")
            vm.typeUnit(" см")
            assertEquals(NewMeasureUi("Предплечье ", " см", canSave = true), vm.state.value.adding)
            vm.confirmAdd()

            assertNull(vm.state.value.adding)
            assertEquals(
                defaults + "Предплечье",
                vm.state.value.rows
                    .map { it.name },
            )
            val added = gym.measures.all(null).last()
            assertEquals("см", added.unit)
            assertNull(added.kind)
            assertEquals(7, added.position)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_measure_without_a_name_cannot_be_added() =
        runTest {
            val vm = viewModel().also { it.load() }

            vm.openAdd()
            vm.typeName("  ")
            vm.typeUnit("см")
            vm.confirmAdd()

            assertFalse(
                vm.state.value.adding!!
                    .canSave,
            )
            assertEquals(
                defaults,
                vm.state.value.rows
                    .map { it.name },
            )

            vm.dismissAdd()
            assertNull(vm.state.value.adding)
        }

    @Test
    fun moving_a_measure_rewrites_the_positions_that_changed_only() =
        runTest {
            val vm = viewModel().also { it.load() }
            vm.toggleOrdering()
            assertTrue(vm.state.value.ordering)
            gym.clock.current = t0 + 5.minutes

            vm.move(measureOf(MeasureKind.Chest).id, 0)

            assertEquals(
                listOf(
                    "Грудь",
                    "Вес",
                    "Талия",
                    "Обхват бёдер",
                    "Бицепс",
                    "Окружность бедра",
                    "Шея",
                ),
                vm.state.value.rows
                    .map { it.name },
            )
            val written =
                gym.measures.rows.values
                    .filter { it.updatedAt != MEASURE_SEEDED_AT }
                    .associate { it.name to it.position }
            assertEquals(mapOf("Грудь" to 0, "Вес" to 1, "Талия" to 2), written)
            assertEquals(1, gym.sync.requests)

            vm.toggleOrdering()
            assertFalse(vm.state.value.ordering)
        }

    @Test
    fun a_switch_lists_the_other_account_s_measures() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val vm = viewModel().also { it.load() }

            gym.accounts.switchTo(misha.account.userId)

            val mishas = gym.measures.all(misha.account.userId).map { it.id }
            assertEquals(7, mishas.size)
            assertEquals(
                mishas,
                vm.state.value.rows
                    .map { it.id },
            )
        }

    @Test
    fun a_finished_sync_shows_the_measures_it_pulled() =
        runTest {
            val vm = viewModel().also { it.load() }

            gym.measures.upsert(
                Measure(MeasureId.random(), null, "Предплечье", "см", null, 9, t0, false),
            )
            gym.sync.completePass()

            assertEquals(
                defaults + "Предплечье",
                vm.state.value.rows
                    .map { it.name },
            )
        }
}
