package monster.greyde.kachalochka.ui.stats

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
import monster.greyde.kachalochka.ui.share.TextSharing
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
    private val press = Machine.new("Leg press", null, t0)
    private val row = Machine.new("Row", null, t0)
    private val gravitron =
        Machine.new("Gravitron", null, t0).copy(weightMode = WeightMode.Counterweight)

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
        texts: TextSharing = on.texts,
    ) = StatisticsViewModel(
        StatisticsArgs(machine),
        on.currentUser,
        on.accounts,
        on.sync,
        on.machineReads,
        on.sets,
        on.profiles,
        on.measures,
        measurements,
        on.deviceClock,
        Nickname(on.profiles, on.accounts),
        texts,
        on.cardShares,
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
        assertEquals("Machines in the period · since 14 October", state.overallTitle)
        assertEquals(
            listOf(
                ProgressCardUi(
                    row.id,
                    "Row",
                    null,
                    "Start",
                    "40 kg × 10",
                    "Best",
                    "40 kg × 12",
                    "+2 reps",
                    improved = true,
                ),
                ProgressCardUi(
                    press.id,
                    "Leg press",
                    null,
                    "Before 14 October",
                    "70 kg × 10",
                    "Since 14 October",
                    "75 kg × 8",
                    "+5 kg",
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

            assertEquals("(-)30 kg × 8" to "(-)27.5 kg × 8", card.from to card.to)
            assertEquals("+2.5 kg", card.change)
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

            assertEquals("−5 kg", cards.getValue(row.id).change)
            assertFalse(cards.getValue(row.id).improved)
            assertEquals("No change", cards.getValue(gravitron.id).change)
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
                listOf("Leg press"),
                vm.state.value.overall
                    .map { it.name },
            )

            vm.toggleImprovementsOnly()

            assertFalse(vm.state.value.improvementsOnly)
            assertEquals(
                listOf("Row", "Leg press", "Gravitron"),
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

            assertEquals("New machine", card.change)
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
    fun export_only_opens_the_share_popup() {
        val vm = viewModel()

        vm.export()

        assertTrue(vm.state.value.shareOffered)
        assertEquals(emptyList(), gym.texts.shared)
        assertEquals(emptyList(), gym.cardShares.shared)
    }

    @Test
    fun export_is_ignored_for_a_machine() {
        val vm = viewModel(press.id)

        vm.export()

        assertFalse(vm.state.value.shareOffered)
    }

    @Test
    fun cancelling_closes_the_share_popup() {
        val vm = viewModel()
        vm.export()

        vm.cancelShare()

        assertFalse(vm.state.value.shareOffered)
    }

    @Test
    fun sharing_as_text_writes_the_shown_cards_under_the_period_and_their_tags() =
        runTest {
            gym.machines.upsert(press.copy(tags = setOf("Legs")))
            record(gravitron, CalendarDay(2023, 11, 2), 27.5 to 8)
            val vm = viewModel()
            vm.toggleGroupByTag()
            vm.export()

            vm.shareText()

            assertEquals(
                listOf(
                    "past month\n\n" +
                        "LEGS\n" +
                        "Leg press +5 kg (75 kg × 8)\n\n" +
                        "Other\n" +
                        "Row +2 reps (40 kg × 12)\n" +
                        "Gravitron — new machine ((-)27.5 kg × 8)",
                ),
                gym.texts.shared,
            )
            assertEquals("Copied", vm.state.value.notice)
            assertFalse(vm.state.value.shareOffered)
        }

    @Test
    fun the_share_popup_closes_before_the_text_is_handed_over() {
        var offeredAtShare: Boolean? = null
        lateinit var vm: StatisticsViewModel
        val texts =
            object : TextSharing {
                override suspend fun share(text: String): String? {
                    offeredAtShare = vm.state.value.shareOffered
                    return null
                }
            }
        vm = viewModel(texts = texts)
        vm.export()

        vm.shareText()

        assertEquals(false, offeredAtShare)
    }

    @Test
    fun sharing_as_image_gives_the_header_and_the_shown_sections_to_draw() {
        val vm = viewModel()
        vm.export()

        vm.shareImage()

        val state = vm.state.value
        assertFalse(state.shareOffered)
        assertEquals(StatsPictureUi("past month", state.overallSections), state.picture)
        assertEquals(emptyList(), gym.cardShares.shared)
    }

    @Test
    fun a_drawn_picture_goes_to_the_card_sharing_and_is_cleared() =
        runTest {
            val vm = viewModel()
            vm.export()
            vm.shareImage()
            val picture = ImageBitmap(1, 1)

            vm.pictureDrawn(picture)

            assertEquals(listOf(picture), gym.cardShares.shared)
            assertNull(vm.state.value.picture)
        }

    @Test
    fun the_image_choice_follows_the_card_sharing() {
        assertTrue(viewModel().state.value.canShareImage)

        gym.cardShares.available = false

        assertFalse(viewModel().state.value.canShareImage)
    }

    @Test
    fun export_starts_with_the_nickname_and_names_the_period() {
        val text = statsExportText("GDM", StatsPeriod.SixMonths, emptyList())

        assertEquals("GDM, past 6 months", text.substringBefore("\n"))
    }

    @Test
    fun a_month_runs_back_from_today_rather_than_from_the_first() {
        val card =
            viewModel()
                .state.value.overall
                .single { it.id == press.id }

        assertEquals("Before 14 October" to "70 kg × 10", card.fromLabel to card.from)
    }

    @Test
    fun a_longer_period_reaches_further_back() {
        val vm = viewModel()

        vm.choosePeriod(StatsPeriod.ThreeMonths)

        val state = vm.state.value
        assertEquals("Machines in the period · since 14 August", state.overallTitle)
        val card = state.overall.single { it.id == press.id }
        assertEquals("Start" to "Best", card.fromLabel to card.toLabel)
        assertEquals("70 kg × 10" to "75 kg × 8", card.from to card.to)
    }

    @Test
    fun the_sort_orders_both_the_cards_and_the_choices() {
        val vm = viewModel()
        assertEquals(StatsSort.Recent, vm.state.value.sort)
        assertEquals(
            listOf("Row", "Leg press", "Gravitron"),
            vm.state.value.choices
                .map { it.name },
        )

        vm.chooseSort(StatsSort.Name)

        assertEquals(
            listOf("Gravitron", "Leg press", "Row"),
            vm.state.value.choices
                .map { it.name },
        )
        assertEquals(
            listOf("Leg press", "Row"),
            vm.state.value.overall
                .map { it.name },
        )
    }

    @Test
    fun growth_puts_the_biggest_weight_gain_first() =
        runTest {
            record(gravitron, CalendarDay(2023, 10, 1), 40.0 to 8)
            record(gravitron, CalendarDay(2023, 11, 2), 30.0 to 8)
            val vm = viewModel()

            vm.chooseSort(StatsSort.Growth)

            assertEquals(
                listOf("Gravitron", "Leg press", "Row"),
                vm.state.value.overall
                    .map { it.name },
            )
        }

    @Test
    fun grouping_by_tags_splits_the_cards_and_the_choices_and_is_kept_in_the_profile() =
        runTest {
            gym.machines.upsert(press.copy(tags = setOf("Legs")))
            gym.machines.upsert(gravitron.copy(tags = setOf("Legs")))
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
                listOf("LEGS" to listOf("Leg press"), "Other" to listOf("Row")),
                vm.state.value.overallSections
                    .map { s -> s.title to s.items.map { it.name } },
            )
            assertEquals(
                listOf(
                    "LEGS" to listOf("Leg press", "Gravitron"),
                    "Other" to listOf("Row"),
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
        assertEquals("75 kg × 8", chosen.best)
        assertEquals(
            listOf(CalendarDay(2023, 11, 10) to 75.0, CalendarDay(2023, 11, 12) to 72.5),
            chosen.points,
        )
        assertEquals(CalendarDay(2023, 10, 14) to gym.today, chosen.start to chosen.end)
        assertFalse(chosen.zeroOnTop)
        assertEquals(
            listOf(
                "12 November" to "72.5kg 1x10",
                "10 November" to "75kg 8-6",
                "10 October" to "70kg 1x10",
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
            gym.machines.upsert(press.copy(tags = setOf("Legs")))
            val vm = viewModel()
            vm.toggleGroupByTag()
            assertTrue(vm.state.value.showsOverall)

            vm.search("pross")

            assertFalse(vm.state.value.showsOverall)
            assertEquals(
                listOf("LEGS" to listOf(press.id to 4..8)),
                vm.state.value.choiceSections
                    .map { s -> s.title to s.items.map { it.id to it.matched } },
            )
            assertFalse(vm.state.value.nothingFound)

            vm.search("squat")

            assertEquals(emptyList(), vm.state.value.choiceSections)
            assertTrue(vm.state.value.nothingFound)
        }

    @Test
    fun a_search_finds_an_own_machine_by_the_name_of_a_linked_friend_s_one() =
        runTest {
            val on = signedInGym()
            on.friends.group("Forest Street Gym", owner = OLEG, ME)
            val olegs = Machine.new("Hack machine", OLEG.userId, t0)
            val (copy, link) = linkedCopy(olegs, ME.userId, t0)
            val mine = copy.copy(name = "Sled squat")
            on.friends.machines += olegs
            on.machines.upsert(mine)
            on.machineLinks.upsert(link)
            val vm = viewModel(on = on)

            vm.search("hack")

            val found = vm.state.value.choices
            assertEquals(listOf(mine.id), found.map { it.id })
            assertNull(found.single().matched)
        }

    @Test
    fun linked_names_read_before_are_searched_at_once() =
        runTest {
            val on = signedInGym()
            val mine = linkedMachine(on)
            on.catalogue.group(ME.userId)
            on.friends.gate = CompletableDeferred()
            val vm = viewModel(on = on)

            vm.search("hack")

            assertEquals(
                listOf(mine.id),
                vm.state.value.choices
                    .map { it.id },
            )
        }

    @Test
    fun a_failed_re_read_keeps_the_linked_names_for_the_search() =
        runTest {
            val on = signedInGym()
            val mine = linkedMachine(on)
            val vm = viewModel(on = on)
            on.friends.offline = true

            on.sync.completePass()
            vm.search("hack")

            assertEquals(
                listOf(mine.id),
                vm.state.value.choices
                    .map { it.id },
            )
        }

    @Test
    fun a_search_takes_effect_at_once_while_the_same_account_reloads() =
        runTest {
            val on = signedInGym()
            val pull = Machine.new("Row", ME.userId, t0)
            on.machines.upsert(pull)
            on.machines.upsert(Machine.new("Bench press", ME.userId, t0))
            val vm = viewModel(on = on)
            on.machines.readGate = CompletableDeferred()
            on.sync.completePass()

            vm.search("row")

            assertEquals(
                listOf(pull.id),
                vm.state.value.choices
                    .map { it.id },
            )
        }

    private fun linkedMachine(on: FakeGym): Machine {
        on.friends.group("Forest Street Gym", owner = OLEG, ME)
        val olegs = Machine.new("Hack machine", OLEG.userId, t0)
        val (copy, link) = linkedCopy(olegs, ME.userId, t0)
        val mine = copy.copy(name = "Sled squat")
        on.friends.machines += olegs
        runBlocking {
            on.machines.upsert(mine)
            on.machineLinks.upsert(link)
        }
        return mine
    }

    @Test
    fun offline_the_own_statistics_show_and_the_search_finds_own_names() =
        runTest {
            val on = signedInGym()
            on.friends.offline = true
            val mine = Machine.new("Row", ME.userId, t0)
            on.machines.upsert(mine)
            val vm = viewModel(on = on)
            assertEquals(
                listOf(mine.id),
                vm.state.value.choices
                    .map { it.id },
            )

            vm.search("row")

            val found = vm.state.value.choices
            assertEquals(listOf(mine.id), found.map { it.id })
            assertEquals(0..2, found.single().matched)
        }

    @Test
    fun choosing_a_machine_ends_the_search() {
        val vm = viewModel()
        vm.search("row")

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
