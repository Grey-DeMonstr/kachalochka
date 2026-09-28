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
import monster.greyde.kachalochka.core.domain.measures.MEASURE_SEEDED_AT
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
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
        listOf("Вес", "Талия", "Грудь", "Бёдра", "Бицепс", "Бедро", "Шея", "Жир")

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    private fun viewModel(on: FakeGym = gym) =
        MeasuresViewModel(
            if (on === gym) counting else on.measures,
            on.measurements,
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
    fun opening_seeds_the_eight_predefined_measures_once() =
        runTest {
            val vm = viewModel().also { it.load() }

            assertEquals(
                defaults,
                vm.state.value.rows
                    .map { it.name },
            )
            assertEquals(8, counting.writes)

            viewModel().load()

            assertEquals(8, counting.writes)
        }

    @Test
    fun a_deleted_predefined_measure_is_not_seeded_again() =
        runTest {
            val waist = missingDefaults(null, emptySet()).first { it.kind == MeasureKind.Waist }
            gym.measures.upsert(waist.copy(deleted = true))

            val vm = viewModel().also { it.load() }

            assertEquals(
                defaults - "Талия",
                vm.state.value.rows
                    .map { it.name },
            )
        }

    @Test
    fun a_row_shows_the_latest_value_its_change_and_how_long_ago() =
        runTest {
            viewModel().load()
            val weight = measureOf(MeasureKind.Weight)
            val fat = measureOf(MeasureKind.BodyFat)
            record(weight, gym.today.plusDays(-9), 83.0)
            record(weight, gym.today.plusDays(-7), 82.4)
            record(weight, gym.today.plusDays(-2), 82.0)
            record(fat, gym.today, 18.0)

            val vm = viewModel().also { it.load() }

            val rows =
                vm.state.value.rows
                    .associateBy { it.id }
            assertEquals(
                MeasureRowUi(weight.id, "Вес", "82 кг", "−0,4", "2 дня назад"),
                rows[weight.id],
            )
            assertEquals(MeasureRowUi(fat.id, "Жир", "18 %", null, "сегодня"), rows[fat.id])
            val waist = measureOf(MeasureKind.Waist)
            assertEquals(MeasureRowUi(waist.id, "Талия", null, null, null), rows[waist.id])
        }

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
            assertEquals(8, added.position)
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
                listOf("Грудь", "Вес", "Талия", "Бёдра", "Бицепс", "Бедро", "Шея", "Жир"),
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
            assertEquals(8, mishas.size)
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
