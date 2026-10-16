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
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Cycle
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.cycleAnchors
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.account.Nickname
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import monster.greyde.kachalochka.ui.measures.cyclePhases
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

    private fun viewModel(
        machine: MachineId? = null,
        on: FakeGym = gym,
        measurements: MeasurementRepository = on.measurements,
    ) = StatisticsViewModel(
        machine,
        on.currentUser,
        on.accounts,
        on.sync,
        on.catalogue,
        on.sets,
        on.profiles,
        on.measures,
        measurements,
        on.clock,
        on.utcOffset,
        Nickname(on.profiles, on.accounts),
        on.texts,
        on.unsaved,
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
    fun a_woman_s_own_machine_chart_shows_her_cycle_phases_for_one_and_three_months() =
        runTest {
            val cycleDay =
                missingDefaults(null, emptySet(), Sex.Female)
                    .single { it.kind == MeasureKind.CycleDay }
            val start = CalendarDay(2023, 11, 1)
            gym.profiles.upsert(Profile.new(null, t0).copy(sex = Sex.Female))
            gym.measures.upsert(cycleDay)
            gym.measurements.upsert(
                Measurement(MeasurementId.random(), null, cycleDay.id, start, 1.0, t0, false),
            )
            val cycle = Cycle(cycleAnchors(mapOf(start to 1.0)), 28)
            val today = CalendarDay(2023, 11, 14)
            val vm = viewModel(press.id)

            val month = CalendarDay(2023, 10, 14)
            assertEquals(
                cyclePhases(cycle, month, today),
                vm.state.value.machine
                    ?.phases,
            )
            vm.choosePeriod(StatsPeriod.ThreeMonths)
            assertEquals(
                CalendarDay(2023, 8, 14),
                vm.state.value.machine
                    ?.phases
                    ?.first,
            )
            vm.choosePeriod(StatsPeriod.SixMonths)
            assertNull(
                vm.state.value.machine
                    ?.phases,
            )
        }

    @Test
    fun a_failed_read_of_the_measures_leaves_the_statistics_without_phases() =
        runTest {
            gym.profiles.upsert(Profile.new(null, t0).copy(sex = Sex.Female))
            val failing =
                object : MeasurementRepository by gym.measurements {
                    override suspend fun all(owner: UserId?): List<Measurement> =
                        throw IllegalStateException("no connection")
                }

            val machine = viewModel(press.id, measurements = failing).state.value.machine

            assertEquals(2, machine?.points?.size)
            assertNull(machine?.phases)
        }

    @Test
    fun without_a_recorded_day_of_the_cycle_the_machine_chart_has_no_phases() {
        assertNull(
            viewModel(press.id)
                .state.value.machine
                ?.phases,
        )
    }

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
                        "НОГИ\n" +
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
                listOf("НОГИ" to listOf("Жим ногами"), "Остальное" to listOf("Тяга")),
                vm.state.value.overallSections
                    .map { s -> s.title to s.items.map { it.name } },
            )
            assertEquals(
                listOf(
                    "НОГИ" to listOf("Жим ногами", "Гравитрон"),
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
    fun a_search_keeps_the_matching_choices_in_their_sections_with_the_matched_part() =
        runTest {
            gym.machines.upsert(press.copy(tags = setOf("Ноги")))
            val vm = viewModel()
            vm.toggleGroupByTag()
            assertTrue(vm.state.value.showsOverall)

            vm.search("ногм")

            assertFalse(vm.state.value.showsOverall)
            assertEquals(
                listOf("НОГИ" to listOf(press.id to 4..8)),
                vm.state.value.choiceSections
                    .map { s -> s.title to s.items.map { it.id to it.matched } },
            )
            assertFalse(vm.state.value.nothingFound)

            vm.search("присед")

            assertEquals(emptyList(), vm.state.value.choiceSections)
            assertTrue(vm.state.value.nothingFound)
        }

    @Test
    fun a_search_finds_an_own_machine_by_the_name_of_a_linked_friend_s_one() =
        runTest {
            val on = signedInGym()
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            val olegs = Machine.new("Гакк-машина", OLEG.userId, t0)
            val (copy, link) = linkedCopy(olegs, ME.userId, t0, emptySet())
            val mine = copy.copy(name = "Присед в тренажёре")
            on.friends.machines += olegs
            on.machines.upsert(mine)
            on.machineLinks.upsert(link)
            val vm = viewModel(on = on)

            vm.search("гакк")

            val found = vm.state.value.choices
            assertEquals(listOf(mine.id), found.map { it.id })
            assertNull(found.single().matched)
        }

    @Test
    fun offline_the_own_statistics_show_and_the_search_finds_own_names() =
        runTest {
            val on = signedInGym()
            on.friends.offline = true
            val mine = Machine.new("Тяга", ME.userId, t0)
            on.machines.upsert(mine)
            val vm = viewModel(on = on)
            assertEquals(
                listOf(mine.id),
                vm.state.value.choices
                    .map { it.id },
            )

            vm.search("тяг")

            val found = vm.state.value.choices
            assertEquals(listOf(mine.id), found.map { it.id })
            assertEquals(0..2, found.single().matched)
        }

    @Test
    fun choosing_a_machine_ends_the_search() {
        val vm = viewModel()
        vm.search("тяг")

        vm.choose(row.id)

        assertEquals("", vm.state.value.query)
        assertTrue(vm.state.value.showsOverall)
        assertEquals(3, vm.state.value.choices.size)
        assertEquals(
            row.id,
            vm.state.value.selected
                ?.id,
        )
    }

    @Test
    fun choosing_overall_again_drops_the_machine() {
        val vm = viewModel(press.id)

        vm.choose(null)

        assertNull(vm.state.value.machine)
        assertNull(vm.state.value.selected)
    }
}
