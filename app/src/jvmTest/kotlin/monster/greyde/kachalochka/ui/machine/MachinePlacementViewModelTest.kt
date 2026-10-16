package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MachinePlacementViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Жим ногами", null, t0)
    private val row = Machine.new("Тяга", null, t0)
    private val legs = Plan(PlanId.random(), null, "Ноги", listOf(row.id), t0, t0, false)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking {
            gym.machines.upsert(press)
            gym.machines.upsert(row)
            gym.plans.upsert(legs)
        }
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(on: FakeGym = gym) =
        MachinePlacementViewModel(
            on.plans,
            on.visits,
            on.machines,
            on.sets,
            on.currentUser,
            on.accounts,
            on.catalogue,
            on.clock,
            on.utcOffset,
            RestTimer(on.clock),
            on.sync,
        )

    @Test
    fun the_choice_lists_every_plan_pinned_first_by_its_title() {
        val unnamed = Plan(PlanId.random(), null, "", listOf(press.id), t0, t0, false, true)
        runBlocking { gym.plans.upsert(unnamed) }
        val vm = viewModel()

        vm.choosePlan(PlacedMachine(press.id))

        assertEquals(
            listOf(PlanChoiceUi(unnamed.id, "Жим ногами"), PlanChoiceUi(legs.id, "Ноги")),
            vm.state.value.choices,
        )
    }

    @Test
    fun a_chosen_plan_takes_the_machine_at_its_end_and_says_so() {
        val vm = viewModel()

        vm.choosePlan(PlacedMachine(press.id))
        vm.addTo(legs.id)

        assertEquals(
            listOf(row.id, press.id),
            gym.plans.rows
                .getValue(legs.id)
                .machineIds,
        )
        assertNull(vm.state.value.choices)
        assertEquals(
            PlacementNoticeUi(press.id, "Добавлено в «Ноги»"),
            vm.state.value.notice,
        )
    }

    @Test
    fun a_plan_holding_the_machine_keeps_it_once() {
        val vm = viewModel()

        vm.choosePlan(PlacedMachine(row.id))
        vm.addTo(legs.id)

        assertEquals(legs, gym.plans.rows.getValue(legs.id))
        assertEquals(
            "Добавлено в «Ноги»",
            vm.state.value.notice
                ?.text,
        )
    }

    @Test
    fun a_new_plan_opens_for_the_machine() {
        val vm = viewModel()
        var opened: MachineId? = null

        vm.choosePlan(PlacedMachine(press.id))
        vm.newPlan { opened = it }

        assertEquals(press.id, opened)
        assertNull(vm.state.value.choices)
    }

    @Test
    fun today_plans_the_machine_in_today_s_visit_and_opens_it() {
        val vm = viewModel()
        var opened: CalendarDay? = null

        vm.planToday(PlacedMachine(press.id)) { opened = it }

        assertEquals(gym.today, opened)
        assertEquals(listOf(press.id), runBlocking { gym.visits.onDay(null, gym.today) }?.planned)
    }

    @Test
    fun a_choice_made_before_a_switch_writes_nothing() {
        val vm = viewModel()
        vm.choosePlan(PlacedMachine(press.id))
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)

        vm.addTo(legs.id)

        assertEquals(legs, gym.plans.rows.getValue(legs.id))
        assertNull(vm.state.value.notice)
    }

    /** Иван's tagged press, with Саша, his child, active on the device. */
    private class SashasGym {
        val gym = signedInGym().withChild(childAccount(SASHA, IVAN_SESSION))
        val ivansPress =
            Machine.new("Жим ногами", ME.userId, gym.clock.current).copy(tags = setOf("ноги"))

        init {
            runBlocking {
                gym.machines.upsert(ivansPress)
                gym.accounts.switchTo(SASHA.userId)
            }
        }
    }

    @Test
    fun another_account_s_machine_goes_in_as_a_new_linked_copy_keeping_its_tags() {
        val sashas = SashasGym()
        val on = sashas.gym
        val vm = viewModel(on)
        var opened: CalendarDay? = null

        vm.planToday(PlacedMachine(sashas.ivansPress.id)) { opened = it }

        val copy = runBlocking { on.machines.all(SASHA.userId) }.single()
        assertEquals("Жим ногами", copy.name)
        assertEquals(setOf("ноги"), copy.tags)
        assertTrue(
            on.machineLinks.rows.values.any {
                it.machineId == copy.id && it.linkedMachineId == sashas.ivansPress.id
            },
        )
        assertEquals(on.today, opened)
        assertEquals(
            listOf(copy.id),
            runBlocking { on.visits.onDay(SASHA.userId, on.today) }?.planned,
        )
    }

    @Test
    fun another_account_s_machine_goes_into_a_plan_as_the_account_s_stand_in() {
        val sashas = SashasGym()
        val on = sashas.gym
        val (copy, link) = linkedCopy(sashas.ivansPress, SASHA.userId, t0)
        val mine = Plan(PlanId.random(), SASHA.userId, "Ноги", emptyList(), t0, t0, false)
        runBlocking {
            on.machines.upsert(copy)
            on.machineLinks.upsert(link)
            on.plans.upsert(mine)
        }
        val vm = viewModel(on)

        vm.choosePlan(PlacedMachine(sashas.ivansPress.id))
        vm.addTo(mine.id)

        assertEquals(
            listOf(copy.id),
            on.plans.rows
                .getValue(mine.id)
                .machineIds,
        )
        assertEquals(listOf(copy), runBlocking { on.machines.all(SASHA.userId) })
    }

    @Test
    fun a_switch_during_the_stand_in_read_writes_and_opens_nothing() {
        val sashas = SashasGym()
        val on = sashas.gym
        val vm = viewModel(on)
        val gate = CompletableDeferred<Unit>()
        on.machines.readGate = gate
        var opened: CalendarDay? = null

        vm.planToday(PlacedMachine(sashas.ivansPress.id)) { opened = it }
        runBlocking { on.accounts.switchTo(ME.userId) }
        gate.complete(Unit)

        assertNull(opened)
        assertTrue(runBlocking { on.machines.all(SASHA.userId) }.isEmpty())
        assertTrue(on.machineLinks.rows.isEmpty())
        assertTrue(on.visits.rows.isEmpty())
    }

    /** Иван's press, with Олег's linked copy of it, and Олег's row, linked to nothing. */
    private class OlegsGym {
        val gym = signedInGym()
        val myPress = Machine.new("Жим ногами", ME.userId, gym.clock.current)
        private val olegCopy = linkedCopy(myPress, OLEG.userId, gym.clock.current)
        val olegPress = olegCopy.first
        val olegRow = Machine.new("Тяга", OLEG.userId, gym.clock.current)

        init {
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friends.machines += listOf(olegPress, olegRow)
            gym.friends.links += olegCopy.second
            runBlocking { gym.machines.upsert(myPress) }
        }
    }

    @Test
    fun a_friend_s_machine_goes_in_as_the_account_s_machine_of_its_cluster() {
        val olegs = OlegsGym()
        val on = olegs.gym
        val mine = Plan(PlanId.random(), ME.userId, "Ноги", emptyList(), t0, t0, false)
        runBlocking { on.plans.upsert(mine) }
        val vm = viewModel(on)
        var stoodIn = false

        vm.choosePlan(PlacedMachine(olegs.olegPress.id, ofFriend = true))
        vm.addTo(mine.id) { stoodIn = true }

        assertEquals(
            listOf(olegs.myPress.id),
            on.plans.rows
                .getValue(mine.id)
                .machineIds,
        )
        assertEquals(listOf(olegs.myPress), runBlocking { on.machines.all(ME.userId) })
        assertTrue(stoodIn)
        assertEquals(
            olegs.olegPress.id,
            vm.state.value.notice
                ?.machine,
        )
    }

    @Test
    fun a_friend_s_machine_without_an_own_one_goes_in_as_a_new_linked_copy() {
        val olegs = OlegsGym()
        val on = olegs.gym
        val vm = viewModel(on)
        var opened: CalendarDay? = null

        vm.planToday(PlacedMachine(olegs.olegRow.id, ofFriend = true)) { opened = it }

        val copy = runBlocking { on.machines.all(ME.userId) }.single { it.name == "Тяга" }
        assertEquals(on.today, opened)
        assertEquals(
            listOf(copy.id),
            runBlocking { on.visits.onDay(ME.userId, on.today) }?.planned,
        )
        assertTrue(
            on.machineLinks.rows.values.any {
                it.machineId == copy.id && it.linkedMachineId == olegs.olegRow.id
            },
        )
    }

    @Test
    fun offline_a_friend_s_machine_goes_nowhere_and_says_so() {
        val olegs = OlegsGym()
        val on = olegs.gym
        on.friends.offline = true
        val vm = viewModel(on)
        var opened: CalendarDay? = null

        vm.planToday(PlacedMachine(olegs.olegRow.id, ofFriend = true)) { opened = it }

        assertNull(opened)
        assertTrue(on.visits.rows.isEmpty())
        assertEquals(
            PlacementNoticeUi(olegs.olegRow.id, "Нет связи с сервером"),
            vm.state.value.notice,
        )
    }

    @Test
    fun a_new_plan_for_a_friend_s_machine_opens_with_the_account_s_machine() {
        val olegs = OlegsGym()
        val vm = viewModel(olegs.gym)
        var opened: MachineId? = null

        vm.choosePlan(PlacedMachine(olegs.olegPress.id, ofFriend = true))
        vm.newPlan { opened = it }

        assertEquals(olegs.myPress.id, opened)
    }

    /** Олег's gym with Саша, Иван's child, beside Иван on the device. */
    private fun olegsGymWithSasha(): OlegsGym {
        val olegs = OlegsGym()
        olegs.gym.withChild(childAccount(SASHA, IVAN_SESSION))
        return olegs
    }

    @Test
    fun a_switch_during_a_friend_s_read_writes_and_opens_nothing() {
        val olegs = olegsGymWithSasha()
        val on = olegs.gym
        val vm = viewModel(on)
        val gate = CompletableDeferred<Unit>()
        on.friends.gate = gate
        var opened: CalendarDay? = null

        vm.planToday(PlacedMachine(olegs.olegRow.id, ofFriend = true)) { opened = it }
        runBlocking { on.accounts.switchTo(SASHA.userId) }
        gate.complete(Unit)

        assertNull(opened)
        assertEquals(listOf(olegs.myPress), runBlocking { on.machines.all(ME.userId) })
        assertTrue(on.visits.rows.isEmpty())
        assertNull(vm.state.value.notice)
    }

    @Test
    fun a_switch_during_a_friend_s_read_leaves_the_plan_as_it_was() {
        val olegs = olegsGymWithSasha()
        val on = olegs.gym
        val mine = Plan(PlanId.random(), ME.userId, "Ноги", emptyList(), t0, t0, false)
        runBlocking { on.plans.upsert(mine) }
        val vm = viewModel(on)
        vm.choosePlan(PlacedMachine(olegs.olegRow.id, ofFriend = true))
        val gate = CompletableDeferred<Unit>()
        on.friends.gate = gate

        vm.addTo(mine.id)
        runBlocking { on.accounts.switchTo(SASHA.userId) }
        gate.complete(Unit)

        assertEquals(mine, on.plans.rows.getValue(mine.id))
        assertEquals(listOf(olegs.myPress), runBlocking { on.machines.all(ME.userId) })
    }

    @Test
    fun a_switch_closes_the_choice_and_drops_the_notice() {
        val olegs = olegsGymWithSasha()
        val on = olegs.gym
        val mine = Plan(PlanId.random(), ME.userId, "Ноги", emptyList(), t0, t0, false)
        runBlocking { on.plans.upsert(mine) }
        val vm = viewModel(on)
        val placed = PlacedMachine(olegs.olegPress.id, ofFriend = true)
        vm.choosePlan(placed)
        vm.addTo(mine.id)
        vm.choosePlan(placed)

        runBlocking { on.accounts.switchTo(SASHA.userId) }

        assertEquals(PlacementUi(), vm.state.value)
        vm.addTo(mine.id)
        assertEquals(
            listOf(olegs.myPress.id),
            on.plans.rows
                .getValue(mine.id)
                .machineIds,
        )
    }

    @Test
    fun a_friend_s_machine_gone_from_the_group_says_so() {
        val olegs = OlegsGym()
        val gone = Machine.new("Гакк", OLEG.userId, t0)
        val vm = viewModel(olegs.gym)
        var opened: CalendarDay? = null

        vm.planToday(PlacedMachine(gone.id, ofFriend = true)) { opened = it }

        assertNull(opened)
        assertEquals(
            PlacementNoticeUi(gone.id, "Упражнение больше недоступно"),
            vm.state.value.notice,
        )
    }

    @Test
    fun a_group_read_the_screen_holds_is_used_and_a_copy_made_from_it_counts() {
        val olegs = OlegsGym()
        val on = olegs.gym
        val held = runBlocking { on.catalogue.group(ME.userId) }
        val reads = on.friends.reads
        val vm = viewModel(on)
        val placed = PlacedMachine(olegs.olegRow.id, ofFriend = true, group = held)

        vm.planToday(placed) {}
        vm.planToday(placed) {}

        assertEquals(reads, on.friends.reads)
        assertEquals(1, runBlocking { on.machines.all(ME.userId) }.count { it.name == "Тяга" })
    }
}
