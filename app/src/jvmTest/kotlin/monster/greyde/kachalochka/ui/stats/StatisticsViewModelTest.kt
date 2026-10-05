package monster.greyde.kachalochka.ui.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.core.domain.gym.StatsSort
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.account.Nickname
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)
    private val gravitron =
        Machine.new("Гравитрон", null, t0).copy(weightMode = WeightMode.Counterweight)

    /** Each day is its own visit, at noon. */
    private suspend fun record(
        machine: Machine,
        day: CalendarDay,
        vararg sets: Pair<Double, Int>,
    ) {
        val visit = VisitId.random()
        sets.forEachIndexed { i, (weight, reps) ->
            val at = Instant.fromEpochSeconds(day.epochDay * 86_400 + 43_200 + i)
            gym.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    visit,
                    machine.id,
                    weight,
                    reps,
                    i,
                    at,
                    at,
                    false,
                ),
            )
        }
    }

    private fun viewModel(machine: MachineId? = null) =
        StatisticsViewModel(
            machine,
            gym.currentUser,
            gym.accounts,
            gym.sync,
            gym.catalogue,
            gym.sets,
            gym.profiles,
            gym.clock,
            gym.utcOffset,
            Nickname(gym.profiles, gym.accounts),
            gym.texts,
        ).also { it.load() }

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            listOf(press, row, gravitron).forEach { gym.machines.upsert(it) }
            record(press, CalendarDay(2023, 10, 10), 70.0 to 10)
            record(press, CalendarDay(2023, 11, 10), 75.0 to 8, 75.0 to 6)
            record(press, CalendarDay(2023, 11, 12), 72.5 to 10)
            record(row, CalendarDay(2023, 11, 13), 40.0 to 10, 40.0 to 12)
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun overall_lists_the_machines_used_in_the_period_last_used_first_with_their_progress() {
        val state = viewModel().state.value

        assertNull(state.selected)
        assertEquals(StatsPeriod.Month, state.period)
        assertEquals("Упражнения за период · с 14 октября", state.overallTitle)
        assertEquals(
            listOf(
                ProgressCardUi(
                    row.id,
                    "Тяга",
                    null,
                    "Начальный",
                    "40 кг × 10",
                    "Лучший",
                    "40 кг × 12",
                    "+2 повт.",
                    improved = true,
                ),
                ProgressCardUi(
                    press.id,
                    "Жим ногами",
                    null,
                    "До 14 октября",
                    "70 кг × 10",
                    "С 14 октября",
                    "75 кг × 8",
                    "+5 кг",
                    improved = true,
                ),
            ),
            state.overall,
        )
    }

    @Test
    fun a_gravitron_s_lighter_weight_is_its_gain() =
        runTest {
            record(gravitron, CalendarDay(2023, 10, 1), 30.0 to 8)
            record(gravitron, CalendarDay(2023, 11, 2), 27.5 to 8)

            val card =
                viewModel()
                    .state.value.overall
                    .single { it.id == gravitron.id }

            assertEquals("(-)30 кг × 8" to "(-)27.5 кг × 8", card.from to card.to)
            assertEquals("+2.5 кг", card.change)
            assertTrue(card.improved)
        }

    @Test
    fun a_weaker_best_set_reads_as_a_loss() =
        runTest {
            record(row, CalendarDay(2023, 10, 1), 45.0 to 10)
            record(gravitron, CalendarDay(2023, 10, 1), 30.0 to 8)
            record(gravitron, CalendarDay(2023, 11, 2), 30.0 to 8)

            val vm = viewModel()
            vm.toggleImprovementsOnly()
            val cards =
                vm.state.value.overall
                    .associateBy { it.id }

            assertEquals("−5 кг", cards.getValue(row.id).change)
            assertFalse(cards.getValue(row.id).improved)
            assertEquals("Без изменений", cards.getValue(gravitron.id).change)
        }

    @Test
    fun improvements_only_is_on_at_first_and_hides_the_machines_without_a_gain() =
        runTest {
            record(row, CalendarDay(2023, 10, 1), 45.0 to 10)
            record(gravitron, CalendarDay(2023, 10, 1), 30.0 to 8)
            record(gravitron, CalendarDay(2023, 11, 2), 30.0 to 8)
            val vm = viewModel()

            assertTrue(vm.state.value.improvementsOnly)
            assertEquals(
                listOf("Жим ногами"),
                vm.state.value.overall
                    .map { it.name },
            )

            vm.toggleImprovementsOnly()

            assertFalse(vm.state.value.improvementsOnly)
            assertEquals(
                listOf("Тяга", "Жим ногами", "Гравитрон"),
                vm.state.value.overall
                    .map { it.name },
            )
        }

    @Test
    fun a_machine_new_in_the_period_without_a_change_is_shown_as_new() =
        runTest {
            record(gravitron, CalendarDay(2023, 11, 2), 27.5 to 8)

            val card =
                viewModel()
                    .state.value.overall
                    .single { it.id == gravitron.id }

            assertEquals("Новое упражнение", card.change)
            assertTrue(card.improved)
            assertTrue(card.newMachine)
        }

    @Test
    fun nothing_improved_says_so_instead_of_an_empty_list() =
        runTest {
            gym.sets.rows.values
                .toList()
                .forEach { gym.sets.upsert(it.copy(deleted = true)) }
            record(row, CalendarDay(2023, 10, 1), 45.0 to 10)
            record(row, CalendarDay(2023, 11, 2), 40.0 to 10)
            val vm = viewModel()

            assertEquals(emptyList(), vm.state.value.overall)
            assertTrue(vm.state.value.nothingImproved)

            vm.toggleImprovementsOnly()
            assertFalse(vm.state.value.nothingImproved)
        }

    @Test
    fun export_writes_the_shown_cards_under_the_period_and_their_tags() =
        runTest {
            gym.machines.upsert(press.copy(tags = setOf("Ноги")))
            record(gravitron, CalendarDay(2023, 11, 2), 27.5 to 8)
            val vm = viewModel()
            vm.toggleGroupByTag()

            vm.export()

            assertEquals(
                listOf(
                    "за месяц\n\n" +
                        "Ноги\n" +
                        "Жим ногами +5 кг (75 кг × 8)\n\n" +
                        "Остальное\n" +
                        "Тяга +2 повт. (40 кг × 12)\n" +
                        "Гравитрон — новое упражнение ((-)27.5 кг × 8)",
                ),
                gym.texts.shared,
            )
            assertEquals("Скопировано", vm.state.value.notice)
        }

    @Test
    fun export_starts_with_the_nickname_and_names_the_period() {
        val text = statsExportText("ГДМ", StatsPeriod.SixMonths, emptyList())

        assertEquals("ГДМ, за 6 месяцев", text.substringBefore("\n"))
    }

    @Test
    fun a_month_runs_back_from_today_rather_than_from_the_first() {
        val card =
            viewModel()
                .state.value.overall
                .single { it.id == press.id }

        assertEquals("До 14 октября" to "70 кг × 10", card.fromLabel to card.from)
    }

    @Test
    fun a_longer_period_reaches_further_back() {
        val vm = viewModel()

        vm.choosePeriod(StatsPeriod.ThreeMonths)

        val state = vm.state.value
        assertEquals("Упражнения за период · с 14 августа", state.overallTitle)
        val card = state.overall.single { it.id == press.id }
        assertEquals("Начальный" to "Лучший", card.fromLabel to card.toLabel)
        assertEquals("70 кг × 10" to "75 кг × 8", card.from to card.to)
    }

    @Test
    fun the_sort_orders_both_the_cards_and_the_choices() {
        val vm = viewModel()
        assertEquals(StatsSort.Recent, vm.state.value.sort)
        assertEquals(
            listOf("Тяга", "Жим ногами", "Гравитрон"),
            vm.state.value.choices
                .map { it.name },
        )

        vm.chooseSort(StatsSort.Name)

        assertEquals(
            listOf("Гравитрон", "Жим ногами", "Тяга"),
            vm.state.value.choices
                .map { it.name },
        )
        assertEquals(
            listOf("Жим ногами", "Тяга"),
            vm.state.value.overall
                .map { it.name },
        )
    }

    @Test
    fun growth_puts_the_biggest_weight_gain_in_percent_first() =
        runTest {
            record(gravitron, CalendarDay(2023, 10, 1), 40.0 to 8)
            record(gravitron, CalendarDay(2023, 11, 2), 30.0 to 8)
            val vm = viewModel()

            vm.chooseSort(StatsSort.Growth)

            assertEquals(
                listOf("Гравитрон", "Жим ногами", "Тяга"),
                vm.state.value.overall
                    .map { it.name },
            )
        }

    @Test
    fun grouping_by_tags_splits_the_cards_and_the_choices_and_is_kept_in_the_profile() =
        runTest {
            gym.machines.upsert(press.copy(tags = setOf("Ноги")))
            gym.machines.upsert(gravitron.copy(tags = setOf("Ноги")))
            val vm = viewModel()
            assertTrue(vm.state.value.canGroupByTag)
            assertFalse(vm.state.value.groupByTag)
            assertEquals(
                listOf(null),
                vm.state.value.overallSections
                    .map { it.title },
            )

            vm.toggleGroupByTag()

            assertTrue(vm.state.value.groupByTag)
            assertEquals(
                listOf("Ноги" to listOf("Жим ногами"), "Остальное" to listOf("Тяга")),
                vm.state.value.overallSections
                    .map { s -> s.title to s.items.map { it.name } },
            )
            assertEquals(
                listOf(
                    "Ноги" to listOf("Жим ногами", "Гравитрон"),
                    "Остальное" to listOf("Тяга"),
                ),
                vm.state.value.choiceSections
                    .map { s -> s.title to s.items.map { it.name } },
            )
            assertEquals(true, gym.profiles.forOwner(null)?.groupByTag)
        }

    @Test
    fun without_tags_there_is_nothing_to_group_by() {
        assertFalse(viewModel().state.value.canGroupByTag)
    }

    @Test
    fun a_machine_charts_each_day_s_best_set_and_lists_every_visit_newest_first() {
        val vm = viewModel()

        vm.choose(press.id)

        val chosen = assertNotNull(vm.state.value.machine)
        assertEquals(
            press.id,
            vm.state.value.selected
                ?.id,
        )
        assertEquals("Лучший подход · с 14 октября", chosen.title)
        assertEquals("75 кг × 8", chosen.best)
        assertEquals(
            listOf(CalendarDay(2023, 11, 10) to 75.0, CalendarDay(2023, 11, 12) to 72.5),
            chosen.points,
        )
        assertEquals(CalendarDay(2023, 10, 14) to gym.today, chosen.start to chosen.end)
        assertFalse(chosen.zeroOnTop)
        assertEquals(
            listOf(
                "12 ноября" to "72.5кг 1x10",
                "10 ноября" to "75кг 8-6",
                "10 октября" to "70кг 1x10",
            ),
            chosen.history.map { it.date to it.results },
        )
    }

    @Test
    fun a_gravitron_charts_its_weights_below_zero() =
        runTest {
            record(gravitron, CalendarDay(2023, 11, 2), 27.5 to 8)

            val chosen = assertNotNull(viewModel(gravitron.id).state.value.machine)

            assertEquals("Лучший подход · противовес · с 14 октября", chosen.title)
            assertEquals(listOf(CalendarDay(2023, 11, 2) to -27.5), chosen.points)
            assertTrue(chosen.zeroOnTop)
        }

    @Test
    fun a_period_without_sets_on_the_machine_has_no_best_and_no_points() {
        val vm = viewModel(press.id)
        vm.choose(gravitron.id)

        val chosen = assertNotNull(vm.state.value.machine)

        assertNull(chosen.best)
        assertEquals(emptyList(), chosen.points)
    }

    @Test
    fun choosing_overall_again_drops_the_machine() {
        val vm = viewModel(press.id)

        vm.choose(null)

        assertNull(vm.state.value.machine)
        assertNull(vm.state.value.selected)
    }
}
