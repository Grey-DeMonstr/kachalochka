package monster.greyde.kachalochka.ui.visit

import kotlinx.coroutines.CompletableDeferred
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
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VisitViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val today = CalendarDay(2023, 11, 14)
    private val seventh = CalendarDay(2023, 11, 7)
    private val visit = Visit(VisitId.random(), null, today, t0, t0, false)
    private val yesterday = VisitId.random()
    private val press =
        Machine
            .new(
                "Жим ногами",
                null,
                t0,
            ).copy(platformWeight = 20.0, setupNote = "Сиденье на 4")
    private val row = Machine.new("Тяга верхнего блока", null, t0)
    private val timer = RestTimer(gym.clock)
    private val lastWeek = Visit(VisitId.random(), null, seventh, t0 - 7.days, t0, false)
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
    private val ivanVisit = Visit(VisitId.random(), ivan.account.userId, today, t0, t0, false)
    private val ivanPress =
        Machine
            .new("Жим ногами", ivan.account.userId, t0)
            .copy(weightStep = 5.0, setupNote = "Сиденье на 4")

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    /** Иван and Миша signed in, Иван active on his own visit and his own machine. */
    private fun twoAccountGym(): FakeGym =
        FakeGym().withAccounts(ivan, misha, active = ivan).also {
            runBlocking {
                it.visits.upsert(ivanVisit)
                it.machines.upsert(ivanPress)
            }
        }

    private fun set(
        visitId: VisitId,
        machine: Machine,
        weight: Double,
        reps: Int,
        minutes: Int,
        owner: UserId? = null,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        owner,
        visitId,
        machine.id,
        weight,
        reps,
        0,
        t0 + minutes.minutes,
        t0,
        false,
    )

    private fun viewModel(
        gym: FakeGym = this.gym,
        day: CalendarDay = today,
    ) = VisitViewModel(
        day,
        gym.visits,
        gym.machines,
        gym.sets,
        gym.currentUser,
        gym.accounts,
        timer,
        gym.clock,
        gym.utcOffset,
        gym.sync,
    )

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            gym.machines.upsert(row)
            listOf(70.0 to 10, 70.0 to 10, 75.0 to 8).forEachIndexed { i, (w, r) ->
                gym.sets.upsert(set(yesterday, press, w, r, -(1.days.inWholeMinutes.toInt()) + i))
            }
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_finished_sync_shows_the_sets_it_pulled() =
        runTest {
            val vm = viewModel()

            gym.sets.upsert(set(visit.id, press, 80.0, 8, 0))
            gym.sync.completePass()

            assertEquals(
                listOf(press.id),
                assertNotNull(vm.state.value).groups.map { it.machineId },
            )
        }

    @Test
    fun before_a_machine_is_chosen_the_sheet_is_empty() {
        val vm = viewModel().also { it.refresh() }

        val state = assertNotNull(vm.state.value)
        assertNull(state.sheet)
        assertEquals("0 подходов", state.setCountLabel)
    }

    @Test
    fun choosing_a_machine_seeds_the_steppers_from_the_previous_visit() {
        val vm = viewModel().also { it.selectMachine(press.id) }

        val sheet = assertNotNull(vm.state.value?.sheet)
        assertEquals("Жим ногами", sheet.name)
        assertEquals("(+20 кг)", sheet.platformSuffix)
        assertEquals("подход 1", sheet.setNumberLabel)
        assertEquals("Сиденье на 4", sheet.caption)
        assertEquals("Вчера · 70×10 · 70×10 · 75×8", sheet.previous)
        assertEquals("70", sheet.weight)
        assertEquals("кг всего · ±2,5", sheet.weightCaption)
        assertEquals("10", sheet.reps)
    }

    @Test
    fun the_steppers_move_by_the_machine_step_and_by_one_rep() {
        val vm = viewModel().also { it.selectMachine(press.id) }

        vm.changeWeight(+1)
        vm.changeReps(-1)

        val sheet = assertNotNull(vm.state.value?.sheet)
        assertEquals("72,5", sheet.weight)
        assertEquals("9", sheet.reps)
    }

    @Test
    fun a_typed_weight_takes_a_comma_or_a_point_and_the_steppers_go_on_from_it() {
        val vm = viewModel().also { it.selectMachine(press.id) }

        vm.typeWeight("22,5")
        assertEquals(
            "22,5",
            vm.state.value
                ?.sheet
                ?.weight,
        )
        vm.changeWeight(+1)
        assertEquals(
            "25",
            vm.state.value
                ?.sheet
                ?.weight,
        )

        vm.typeWeight("22.75")
        vm.changeWeight(-1)
        assertEquals(
            "20,25",
            vm.state.value
                ?.sheet
                ?.weight,
        )
    }

    @Test
    fun a_weight_that_is_not_a_number_is_not_saved() =
        runTest {
            val vm = viewModel().also { it.selectMachine(press.id) }

            listOf("7,,5", "", "-5").forEach {
                vm.typeWeight(it)
                assertEquals(
                    false,
                    vm.state.value
                        ?.sheet
                        ?.canSave,
                    it,
                )
            }
            vm.save()

            assertEquals(emptyList(), gym.sets.forVisit(visit.id))
        }

    @Test
    fun the_typed_weight_is_saved_and_the_next_set_is_suggested_again() =
        runTest {
            val vm = viewModel().also { it.selectMachine(press.id) }
            vm.typeWeight("22,5")

            vm.save()

            assertEquals(
                22.5,
                gym.sets
                    .forVisit(visit.id)
                    .single()
                    .weight,
            )
            val sheet = assertNotNull(vm.state.value?.sheet)
            assertEquals("70", sheet.weight)
            assertEquals(true, sheet.canSave)
        }

    @Test
    fun a_typed_weight_survives_a_sync_reload_and_collapse_expand() {
        val vm = viewModel().also { it.selectMachine(press.id) }
        vm.typeWeight("22,5")

        gym.sync.completePass()
        vm.collapseSheet()
        vm.expandSheet()

        assertEquals(
            "22,5",
            vm.state.value
                ?.sheet
                ?.weight,
        )
    }

    @Test
    fun a_refresh_keeps_the_stepper_values() {
        val vm = viewModel().also { it.selectMachine(press.id) }
        vm.changeWeight(+1)
        vm.changeReps(-1)

        vm.refresh()

        val sheet = assertNotNull(vm.state.value?.sheet)
        assertEquals("72,5", sheet.weight)
        assertEquals("9", sheet.reps)
    }

    @Test
    fun saving_records_the_set_starts_the_rest_and_moves_to_the_next_set() =
        runTest {
            val vm = viewModel().also { it.selectMachine(press.id) }
            vm.changeWeight(-1)

            vm.save()

            val saved = gym.sets.forVisit(visit.id).single()
            assertEquals(67.5 to 10, saved.weight to saved.reps)
            assertEquals(t0, timer.startedAt.value)
            val state = assertNotNull(vm.state.value)
            assertEquals("1 подход", state.setCountLabel)
            assertEquals("подход 2", state.sheet?.setNumberLabel)
            assertEquals("70", state.sheet?.weight)
            assertEquals(
                listOf("Жим ногами (+20 кг)" to "67,5 кг"),
                state.groups.map { it.title to it.summary },
            )
        }

    @Test
    fun a_second_tap_while_saving_records_one_set() =
        runTest {
            val vm = viewModel().also { it.selectMachine(press.id) }
            val gate = CompletableDeferred<Unit>().also { gym.sets.gate = it }

            vm.save()
            vm.save()
            gate.complete(Unit)

            assertEquals(1, gym.sets.forVisit(visit.id).size)
        }

    @Test
    fun a_group_expands_into_its_sets() =
        runTest {
            gym.sets.upsert(set(visit.id, row, 45.0, 12, 1))
            gym.sets.upsert(set(visit.id, row, 45.0, 10, 2))
            val vm = viewModel().also { it.refresh() }

            vm.toggleGroup(row.id)

            val group = assertNotNull(vm.state.value).groups.single()
            assertEquals("2 × 45 кг", group.summary)
            assertEquals(true, group.expanded)
            assertEquals(
                listOf(
                    "Тяга верхнего блока · подход 1" to "45 кг × 12",
                    "Тяга верхнего блока · подход 2" to "45 кг × 10",
                ),
                group.sets.map { it.title to it.value },
            )
        }

    @Test
    fun a_machine_with_an_own_unit_shows_it_in_the_list_and_the_sheet() =
        runTest {
            val gravitron =
                Machine
                    .new("Гравитрон", null, t0)
                    .copy(unit = WeightUnit.Custom, unitLabel = "плитка", weightStep = 1.0)
            gym.machines.upsert(gravitron)
            gym.sets.upsert(set(visit.id, gravitron, 7.0, 10, 0))

            val vm = viewModel().also { it.selectMachine(gravitron.id) }

            val state = assertNotNull(vm.state.value)
            assertEquals("7 плитка", state.groups.single().summary)
            assertEquals("плитка всего · ±1", state.sheet?.weightCaption)
        }

    @Test
    fun editing_a_set_shows_what_was_recorded_and_when() =
        runTest {
            val first = set(visit.id, press, 60.0, 10, 0)
            val second = set(visit.id, press, 70.0, 10, 1)
            gym.sets.upsert(first)
            gym.sets.upsert(second)
            val vm = viewModel().also { it.refresh() }

            vm.editSet(second.id)

            val state = assertNotNull(vm.state.value)
            val sheet = assertNotNull(state.sheet)
            assertEquals(true, sheet.editing)
            assertEquals("подход 2", sheet.setNumberLabel)
            assertEquals("Правка · записано 22:14, было 70 кг × 10", sheet.caption)
            assertNull(sheet.previous)
            assertEquals("70", sheet.weight)
            assertEquals(
                listOf(false, true),
                state.groups
                    .single()
                    .sets
                    .map { it.selected },
            )
        }

    @Test
    fun saving_an_edit_rewrites_the_set_without_restarting_the_rest() =
        runTest {
            val recorded = set(visit.id, press, 70.0, 10, 0)
            gym.sets.upsert(recorded)
            val vm = viewModel().also { it.refresh() }
            vm.editSet(recorded.id)

            vm.changeWeight(+1)
            vm.save()

            val saved = gym.sets.forVisit(visit.id).single()
            assertEquals(recorded.id, saved.id)
            assertEquals(72.5, saved.weight)
            assertNull(timer.startedAt.value)
            assertEquals(
                false,
                vm.state.value
                    ?.sheet
                    ?.editing,
            )
        }

    @Test
    fun deleting_the_edited_set_removes_it_from_the_visit() =
        runTest {
            val recorded = set(visit.id, press, 70.0, 10, 0)
            gym.sets.upsert(recorded)
            val vm = viewModel().also { it.refresh() }
            vm.editSet(recorded.id)

            vm.deleteEditedSet()

            assertEquals(emptyList(), gym.sets.forVisit(visit.id))
            assertEquals(true, gym.sets.rows[recorded.id]?.deleted)
            assertEquals("0 подходов", vm.state.value?.setCountLabel)
        }

    @Test
    fun a_chosen_machine_opens_the_sheet_expanded() {
        val vm = viewModel().also { it.selectMachine(press.id) }

        assertEquals(
            true,
            vm.state.value
                ?.sheet
                ?.expanded,
        )
    }

    @Test
    fun collapsing_keeps_the_machine_and_the_stepper_values() {
        val vm = viewModel().also { it.selectMachine(press.id) }
        vm.changeWeight(+1)

        assertTrue(vm.collapseSheet())

        val collapsed = assertNotNull(vm.state.value?.sheet)
        assertEquals(false, collapsed.expanded)
        assertEquals("Жим ногами", collapsed.name)
        assertEquals(press.id, vm.selectedMachineId)
        assertFalse(vm.collapseSheet())

        vm.expandSheet()

        val expanded = assertNotNull(vm.state.value?.sheet)
        assertEquals(true, expanded.expanded)
        assertEquals("72,5", expanded.weight)
    }

    @Test
    fun without_a_machine_there_is_nothing_to_collapse() {
        val vm = viewModel().also { it.refresh() }

        assertFalse(vm.collapseSheet())
    }

    @Test
    fun tapping_a_set_while_collapsed_opens_the_sheet_on_it() =
        runTest {
            val recorded = set(visit.id, press, 70.0, 10, 0)
            gym.sets.upsert(recorded)
            val vm = viewModel().also { it.selectMachine(row.id) }
            vm.collapseSheet()

            vm.editSet(recorded.id)

            val sheet = assertNotNull(vm.state.value?.sheet)
            assertEquals(true, sheet.expanded)
            assertEquals(true, sheet.editing)
        }

    @Test
    fun collapsing_while_editing_leaves_edit_mode_on_the_same_machine() =
        runTest {
            val recorded = set(visit.id, press, 70.0, 10, 0)
            gym.sets.upsert(recorded)
            val vm = viewModel().also { it.refresh() }
            vm.editSet(recorded.id)

            assertTrue(vm.collapseSheet())

            val sheet = assertNotNull(vm.state.value?.sheet)
            assertEquals(false, sheet.editing)
            assertEquals(false, sheet.expanded)
            assertEquals("подход 2", sheet.setNumberLabel)
        }

    @Test
    fun saving_as_another_account_records_into_that_account_s_own_visit() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two).also { it.selectMachine(ivanPress.id) }

            vm.switchTo(misha.account.userId)
            vm.save()

            val saved =
                two.sets.rows.values
                    .single()
            assertEquals(misha.account.userId, saved.userId)
            assertEquals(two.visits.onDay(misha.account.userId, today)?.id, saved.visitId)
            assertNotEquals(ivanVisit.id, saved.visitId)
        }

    @Test
    fun switching_shows_the_other_account_s_visit_without_creating_one() =
        runTest {
            val two = twoAccountGym()
            two.sets.upsert(set(ivanVisit.id, ivanPress, 70.0, 10, 0, ivan.account.userId))
            val vm = viewModel(two).also { it.selectMachine(ivanPress.id) }
            assertEquals("1 подход", vm.state.value?.setCountLabel)

            vm.switchTo(misha.account.userId)

            assertEquals("0 подходов", vm.state.value?.setCountLabel)
            assertEquals(
                listOf(ivanVisit.id),
                two.visits.rows.keys
                    .toList(),
            )
        }

    @Test
    fun the_visit_a_save_lands_in_is_the_one_on_screen() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two).also { it.selectMachine(ivanPress.id) }
            vm.switchTo(misha.account.userId)

            vm.save()

            val saved =
                two.sets.rows.values
                    .single()
            val state = assertNotNull(vm.state.value)
            assertEquals("1 подход", state.setCountLabel)
            assertEquals(
                listOf(saved.id),
                state.groups
                    .single()
                    .sets
                    .map { it.id },
            )
            assertEquals(emptyList(), two.sets.forVisit(ivanVisit.id))
        }

    @Test
    fun the_sheet_names_the_account_a_save_would_record_as() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two).also { it.selectMachine(ivanPress.id) }
            assertEquals(
                "Сохранить · Иван",
                vm.state.value
                    ?.sheet
                    ?.saveLabel,
            )

            vm.switchTo(misha.account.userId)

            val sheet = assertNotNull(vm.state.value?.sheet)
            assertEquals("Сохранить · Миша", sheet.saveLabel)
            assertEquals(
                listOf("Иван" to false, "Миша" to true),
                sheet.people.map {
                    it.displayName to it.active
                },
            )
        }

    @Test
    fun saving_as_another_account_mirrors_the_machine_to_them() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two).also { it.selectMachine(ivanPress.id) }

            vm.switchTo(misha.account.userId)
            vm.save()

            val mishaPress = assertNotNull(two.machines.named(misha.account.userId, "Жим ногами"))
            assertNotEquals(ivanPress.id, mishaPress.id)
            assertEquals(ivanPress.weightStep, mishaPress.weightStep)
            assertEquals(
                mishaPress.id,
                two.sets.rows.values
                    .single()
                    .machineId,
            )
        }

    @Test
    fun a_second_set_as_the_same_account_reuses_the_mirrored_machine() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two).also { it.selectMachine(ivanPress.id) }
            vm.switchTo(misha.account.userId)

            vm.save()
            vm.save()

            assertEquals(2, two.sets.rows.size)
            assertEquals(
                1,
                two.machines.rows.values
                    .count { it.userId == misha.account.userId },
            )
        }

    @Test
    fun opening_machine_settings_after_a_switch_mirrors_the_machine_first() =
        runTest {
            val two = twoAccountGym()
            val vm = viewModel(two).also { it.selectMachine(ivanPress.id) }
            vm.switchTo(misha.account.userId)
            var opened: MachineId? = null

            vm.openMachineSettings { opened = it }

            val mishaPress = assertNotNull(two.machines.named(misha.account.userId, "Жим ногами"))
            assertEquals(mishaPress.id, opened)
            assertNotEquals(ivanPress.id, opened)
            assertEquals(ivanPress.setupNote, mishaPress.setupNote)
        }

    @Test
    fun an_edit_keeps_the_set_with_its_own_owner_and_visit() =
        runTest {
            val two = twoAccountGym()
            val recorded = set(ivanVisit.id, ivanPress, 70.0, 10, 0, ivan.account.userId)
            two.sets.upsert(recorded)
            val vm = viewModel(two).also { it.refresh() }
            vm.editSet(recorded.id)

            two.accounts.switchTo(misha.account.userId)
            vm.changeWeight(+1)
            vm.save()

            val saved = two.sets.rows.getValue(recorded.id)
            assertEquals(75.0, saved.weight)
            assertEquals(ivan.account.userId, saved.userId)
            assertEquals(ivanVisit.id, saved.visitId)
            assertEquals(ivanPress.id, saved.machineId)
            assertEquals(
                listOf(ivanVisit.id),
                two.visits.rows.keys
                    .toList(),
            )
        }

    @Test
    fun today_s_visit_is_titled_today() {
        val state = assertNotNull(viewModel().also { it.refresh() }.state.value)

        assertEquals("Сегодня", state.title)
    }

    @Test
    fun another_day_s_visit_is_titled_with_its_date() =
        runTest {
            gym.visits.upsert(lastWeek)

            val vm = viewModel(day = seventh).also { it.refresh() }

            val state = assertNotNull(vm.state.value)
            assertEquals("Визит · 7 ноября", state.title)
        }

    @Test
    fun a_set_added_to_another_day_s_visit_lands_after_its_last_set_without_a_rest() =
        runTest {
            gym.visits.upsert(lastWeek)
            val last = set(lastWeek.id, press, 70.0, 10, -(7.days.inWholeMinutes.toInt()) + 5)
            gym.sets.upsert(last)
            val vm = viewModel(day = seventh).also { it.selectMachine(press.id) }

            vm.save()

            val added = gym.sets.forVisit(lastWeek.id).last()
            assertNotEquals(last.id, added.id)
            assertEquals(last.recordedAt + 1.seconds, added.recordedAt)
            assertNull(timer.startedAt.value)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_visit_no_client_has_dated_opens_on_its_day_and_takes_the_day_s_new_sets() =
        runTest {
            val undated = lastWeek.copy(day = null)
            gym.visits.upsert(undated)
            gym.sets.upsert(set(undated.id, press, 70.0, 10, -(7.days.inWholeMinutes.toInt())))
            val vm = viewModel(day = seventh).also { it.selectMachine(press.id) }

            assertEquals(
                listOf(press.id),
                assertNotNull(vm.state.value).groups.map { it.machineId },
            )

            vm.save()

            assertEquals(2, gym.sets.forVisit(undated.id).size)
            assertNull(gym.visits.onDay(null, seventh))
        }

    @Test
    fun another_day_s_visit_suggests_from_the_visit_before_it() =
        runTest {
            gym.visits.upsert(lastWeek)
            val nineDaysAgo = -(9.days.inWholeMinutes.toInt())
            gym.sets.upsert(set(VisitId.random(), press, 50.0, 8, nineDaysAgo))
            val vm = viewModel(day = seventh).also { it.selectMachine(press.id) }

            val sheet = assertNotNull(vm.state.value?.sheet)
            assertEquals("2 дня назад · 50×8", sheet.previous)
            assertEquals("50", sheet.weight)
        }

    @Test
    fun another_day_offers_no_person_chips() =
        runTest {
            val two = twoAccountGym()
            val thirteenth = CalendarDay(2023, 11, 13)
            two.visits.upsert(ivanVisit.copy(id = VisitId.random(), day = thirteenth))
            val vm = viewModel(two, thirteenth).also { it.selectMachine(ivanPress.id) }

            val sheet = assertNotNull(vm.state.value?.sheet)
            assertEquals(emptyList(), sheet.people)
            assertEquals("Сохранить подход", sheet.saveLabel)
        }

    @Test
    fun deleting_a_set_of_another_day_asks_for_a_sync_pass() =
        runTest {
            gym.visits.upsert(lastWeek)
            val recorded = set(lastWeek.id, press, 70.0, 10, -(7.days.inWholeMinutes.toInt()))
            gym.sets.upsert(recorded)
            val vm = viewModel(day = seventh).also { it.refresh() }
            vm.editSet(recorded.id)

            vm.deleteEditedSet()

            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun an_edit_of_another_day_asks_for_a_sync_pass() =
        runTest {
            gym.visits.upsert(lastWeek)
            val recorded = set(lastWeek.id, press, 70.0, 10, -(7.days.inWholeMinutes.toInt()))
            gym.sets.upsert(recorded)
            val vm = viewModel(day = seventh).also { it.refresh() }
            vm.editSet(recorded.id)

            vm.save()

            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_finished_sync_pass_on_another_day_asks_for_no_other() =
        runTest {
            gym.visits.upsert(lastWeek)
            viewModel(day = seventh).also { it.selectMachine(press.id) }

            gym.sync.completePass()

            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_set_added_today_asks_for_no_sync_pass() =
        runTest {
            val vm = viewModel().also { it.selectMachine(press.id) }

            vm.save()

            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun the_first_set_of_another_day_creates_its_visit_at_local_noon() =
        runTest {
            val tenth = CalendarDay(2023, 11, 10)
            val vm = viewModel(day = tenth).also { it.selectMachine(press.id) }
            assertNull(gym.visits.onDay(null, tenth))

            vm.save()

            val created = assertNotNull(gym.visits.onDay(null, tenth))
            assertEquals(Instant.parse("2023-11-10T12:00:00Z"), created.recordedAt)
            assertEquals(
                created.recordedAt + 1.seconds,
                gym.sets
                    .forVisit(created.id)
                    .single()
                    .recordedAt,
            )
            assertNull(timer.startedAt.value)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun the_first_set_of_today_creates_today_s_visit_now() =
        runTest {
            val fresh = FakeGym()
            fresh.machines.upsert(press)
            val vm = viewModel(fresh).also { it.selectMachine(press.id) }

            vm.save()

            val created = assertNotNull(fresh.visits.onDay(null, today))
            assertEquals(t0, created.recordedAt)
            assertEquals(
                t0,
                fresh.sets
                    .forVisit(created.id)
                    .single()
                    .recordedAt,
            )
            assertEquals(t0, timer.startedAt.value)
        }

    @Test
    fun order_mode_collapses_the_sheet_and_opens_every_group() =
        runTest {
            gym.sets.upsert(set(visit.id, press, 70.0, 10, 0))
            gym.sets.upsert(set(visit.id, row, 45.0, 12, 1))
            val vm = viewModel().also { it.selectMachine(press.id) }

            vm.toggleOrdering()

            val state = assertNotNull(vm.state.value)
            assertEquals(true, state.ordering)
            assertEquals(false, state.sheet?.expanded)
            assertEquals(listOf(true, true), state.groups.map { it.expanded })
            assertEquals(
                listOf(false to true, true to false),
                state.groups.map { it.canMoveUp to it.canMoveDown },
            )
        }

    @Test
    fun moving_a_machine_up_puts_it_first_and_keeps_order_mode() =
        runTest {
            gym.sets.upsert(set(visit.id, press, 70.0, 10, 0))
            gym.sets.upsert(set(visit.id, row, 45.0, 12, 1))
            val vm = viewModel().also { it.refresh() }
            vm.toggleOrdering()

            vm.moveMachine(row.id, -1)

            val state = assertNotNull(vm.state.value)
            assertEquals(listOf(row.id, press.id), state.groups.map { it.machineId })
            assertEquals(true, state.ordering)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun moving_a_set_down_swaps_it_with_the_next_one() =
        runTest {
            val first = set(visit.id, press, 60.0, 10, 0)
            val second = set(visit.id, press, 70.0, 10, 1)
            gym.sets.upsert(first)
            gym.sets.upsert(second)
            val vm = viewModel().also { it.refresh() }
            vm.toggleOrdering()

            vm.moveSet(first.id, +1)

            val rows = assertNotNull(vm.state.value).groups.single().sets
            assertEquals(listOf(second.id, first.id), rows.map { it.id })
            assertEquals(
                listOf(false to true, true to false),
                rows.map { it.canMoveUp to it.canMoveDown },
            )
        }

    @Test
    fun a_reorder_on_another_day_asks_for_a_sync_pass() =
        runTest {
            gym.visits.upsert(lastWeek)
            val base = -(7.days.inWholeMinutes.toInt())
            gym.sets.upsert(set(lastWeek.id, press, 70.0, 10, base))
            gym.sets.upsert(set(lastWeek.id, row, 45.0, 12, base + 1))
            val vm = viewModel(day = seventh).also { it.refresh() }
            vm.toggleOrdering()

            vm.moveMachine(row.id, -1)

            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_set_does_not_open_while_ordering() =
        runTest {
            val recorded = set(visit.id, press, 70.0, 10, 0)
            gym.sets.upsert(recorded)
            val vm = viewModel().also { it.refresh() }
            vm.toggleOrdering()

            vm.editSet(recorded.id)

            assertNull(vm.state.value?.sheet)
        }

    @Test
    fun choosing_a_machine_ends_order_mode() {
        val vm = viewModel().also { it.refresh() }
        vm.toggleOrdering()

        vm.selectMachine(press.id)

        val state = assertNotNull(vm.state.value)
        assertEquals(false, state.ordering)
        assertEquals(true, state.sheet?.expanded)
    }

    @Test
    fun expanding_the_sheet_ends_order_mode() {
        val vm = viewModel().also { it.selectMachine(press.id) }
        vm.toggleOrdering()

        vm.expandSheet()

        val state = assertNotNull(vm.state.value)
        assertEquals(false, state.ordering)
        assertEquals(true, state.sheet?.expanded)
    }

    @Test
    fun a_new_set_goes_after_every_set_of_the_visit() =
        runTest {
            gym.sets.upsert(set(visit.id, row, 45.0, 12, 0))
            val vm = viewModel().also { it.selectMachine(press.id) }

            vm.save()

            val saved = gym.sets.forVisit(visit.id).single { it.machineId == press.id }
            assertEquals(1, saved.position)
        }

    @Test
    fun a_new_set_takes_the_position_after_the_visit_s_highest() =
        runTest {
            gym.sets.upsert(set(visit.id, row, 45.0, 12, 0).copy(position = 3))
            val vm = viewModel().also { it.selectMachine(press.id) }

            vm.save()

            val saved = gym.sets.forVisit(visit.id).single { it.machineId == press.id }
            assertEquals(4, saved.position)
        }

    @Test
    fun a_new_set_on_another_day_takes_the_position_after_its_visit_s_highest() =
        runTest {
            gym.visits.upsert(lastWeek)
            val base = -(7.days.inWholeMinutes.toInt())
            gym.sets.upsert(set(lastWeek.id, row, 45.0, 12, base).copy(position = 3))
            val vm = viewModel(day = seventh).also { it.selectMachine(press.id) }

            vm.save()

            val saved = gym.sets.forVisit(lastWeek.id).single { it.machineId == press.id }
            assertEquals(4, saved.position)
        }

    @Test
    fun two_saves_on_an_empty_past_day_record_into_one_visit() =
        runTest {
            val tenth = CalendarDay(2023, 11, 10)
            val vm = viewModel(day = tenth).also { it.selectMachine(press.id) }

            vm.save()
            vm.save()

            val created = assertNotNull(gym.visits.onDay(null, tenth))
            assertEquals(
                listOf(visit.id, created.id),
                gym.visits.rows.keys
                    .toList(),
            )
            assertEquals(2, gym.sets.forVisit(created.id).size)
        }

    @Test
    fun a_finished_sync_while_ordering_keeps_order_mode_and_the_new_order() =
        runTest {
            gym.sets.upsert(set(visit.id, press, 70.0, 10, 0))
            gym.sets.upsert(set(visit.id, row, 45.0, 12, 1))
            val vm = viewModel().also { it.refresh() }
            vm.toggleOrdering()
            vm.moveMachine(row.id, -1)

            gym.sync.completePass()

            val state = assertNotNull(vm.state.value)
            assertEquals(true, state.ordering)
            assertEquals(listOf(row.id, press.id), state.groups.map { it.machineId })
        }

    @Test
    fun opening_a_day_without_a_visit_writes_nothing() {
        viewModel(day = CalendarDay(2023, 11, 10)).also { it.refresh() }

        assertEquals(
            listOf(visit.id),
            gym.visits.rows.keys
                .toList(),
        )
    }
}
