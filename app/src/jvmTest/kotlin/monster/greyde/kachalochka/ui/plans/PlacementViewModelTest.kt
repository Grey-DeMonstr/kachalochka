package monster.greyde.kachalochka.ui.plans

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
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import monster.greyde.kachalochka.ui.machine.PlacedMachine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class PlacementViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val press = Machine.new("Leg press", null, t0)
    private val row = Machine.new("Row", null, t0)
    private val curl = Machine.new("Biceps curl", null, t0)
    private val legs = Plan(PlanId.random(), null, "Legs", listOf(row.id), t0, t0, false)
    private val visit = Visit(VisitId.random(), null, gym.today, t0, t0, false, listOf(curl.id))

    private fun set(
        machine: Machine,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        null,
        visit.id,
        machine.id,
        60.0,
        10,
        0,
        t0 + minutes.minutes,
        t0,
        false,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking {
            listOf(press, row, curl).forEach { gym.machines.upsert(it) }
            gym.plans.upsert(legs)
        }
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(on: FakeGym = gym) =
        PlacementViewModel(on.placement, on.accounts, on.catalogue)

    /** Today's visit with a set on the press, then on the row, and the curl planned. */
    private fun seedVisit() =
        runBlocking {
            gym.visits.upsert(visit)
            gym.sets.upsert(set(press, 0))
            gym.sets.upsert(set(row, 1))
        }

    private fun PlacementViewModel.choosePlan(machine: PlacedMachine) =
        choosePlan(machine.id.value, listOf(machine))

    private fun PlacementViewModel.planToday(
        machine: PlacedMachine,
        onStoodIn: () -> Unit = {},
        onOpen: (CalendarDay) -> Unit,
    ) = planToday(machine.id.value, listOf(machine), onStoodIn, onOpen)

    @Test
    fun the_choice_lists_every_plan_pinned_first_by_its_title() {
        val unnamed = Plan(PlanId.random(), null, "", listOf(press.id), t0, t0, false, true)
        runBlocking { gym.plans.upsert(unnamed) }
        val vm = viewModel()

        vm.choosePlan(PlacedMachine(press.id))

        assertEquals(
            listOf(PlanChoiceUi(unnamed.id, "Leg press"), PlanChoiceUi(legs.id, "Legs")),
            vm.state.value.choices,
        )
    }

    @Test
    fun a_visit_s_choice_lists_every_plan_by_its_title() {
        seedVisit()
        val vm = viewModel()

        vm.choosePlan(gym.today)

        assertEquals(listOf(PlanChoiceUi(legs.id, "Legs")), vm.state.value.choices)
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
            PlacementNoticeUi(press.id.value, "Added to “Legs”"),
            vm.state.value.notice,
        )
    }

    @Test
    fun a_chosen_plan_takes_the_visit_s_machines_it_lacks_at_its_end_and_says_so() {
        seedVisit()
        val vm = viewModel()

        vm.choosePlan(gym.today)
        vm.addTo(legs.id)

        assertEquals(
            listOf(row.id, press.id, curl.id),
            gym.plans.rows
                .getValue(legs.id)
                .machineIds,
        )
        assertNull(vm.state.value.choices)
        assertEquals(
            PlacementNoticeUi(gym.today.iso, "Added to “Legs”"),
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
            "Added to “Legs”",
            vm.state.value.notice
                ?.text,
        )
    }

    @Test
    fun a_new_plan_opens_for_the_machine() {
        val vm = viewModel()
        var opened: List<MachineId>? = null

        vm.choosePlan(PlacedMachine(press.id))
        vm.newPlan(onOpenMachines = { opened = it })

        assertEquals(listOf(press.id), opened)
        assertNull(vm.state.value.choices)
    }

    @Test
    fun a_new_plan_opens_for_the_visit_s_day() {
        seedVisit()
        val vm = viewModel()
        var opened: CalendarDay? = null

        vm.choosePlan(gym.today)
        vm.newPlan(onOpen = { opened = it })

        assertEquals(gym.today, opened)
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

    @Test
    fun a_visit_s_choice_made_before_a_switch_writes_nothing() {
        seedVisit()
        val vm = viewModel()
        vm.choosePlan(gym.today)
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)

        vm.addTo(legs.id)

        assertEquals(legs, gym.plans.rows.getValue(legs.id))
        assertNull(vm.state.value.notice)
    }

    /** Ivan's tagged press, with Sasha, his child, active on the device. */
    private class SashasGym {
        val gym = signedInGym().withChild(childAccount(SASHA, IVAN_SESSION))
        val ivansPress =
            Machine.new("Leg press", ME.userId, gym.clock.current).copy(tags = setOf("legs"))

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
        assertEquals("Leg press", copy.name)
        assertEquals(setOf("legs"), copy.tags)
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
        val mine = Plan(PlanId.random(), SASHA.userId, "Legs", emptyList(), t0, t0, false)
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

    /**
     * Ivan's press with Oleg's linked copy of it, Oleg's tagged row linked to nothing, and Ivan's
     * empty plan. A card of Oleg's shows his press and his row.
     */
    private class OlegsGym {
        val gym = signedInGym()
        val myPress = Machine.new("Leg press", ME.userId, gym.clock.current)
        private val olegCopy = linkedCopy(myPress, OLEG.userId, gym.clock.current)
        val olegPress = olegCopy.first
        val olegRow =
            Machine.new("Row", OLEG.userId, gym.clock.current).copy(tags = setOf("back"))
        val card = listOf(olegPress, olegRow).map { PlacedMachine(it.id, ofFriend = true) }
        val mine =
            Plan(
                PlanId.random(),
                ME.userId,
                "Legs",
                emptyList(),
                gym.clock.current,
                gym.clock.current,
                false,
            )

        init {
            gym.friends.group("Forest Street Gym", owner = OLEG, ME)
            gym.friends.machines += listOf(olegPress, olegRow)
            gym.friends.links += olegCopy.second
            runBlocking {
                gym.machines.upsert(myPress)
                gym.plans.upsert(mine)
            }
        }

        fun ownRow() = runBlocking { gym.machines.all(ME.userId) }.single { it.name == "Row" }
    }

    @Test
    fun a_friend_s_machine_goes_in_as_the_account_s_machine_of_its_cluster() {
        val olegs = OlegsGym()
        val on = olegs.gym
        val vm = viewModel(on)
        var stoodIn = false

        vm.choosePlan(PlacedMachine(olegs.olegPress.id, ofFriend = true))
        vm.addTo(olegs.mine.id) { stoodIn = true }

        assertEquals(
            listOf(olegs.myPress.id),
            on.plans.rows
                .getValue(olegs.mine.id)
                .machineIds,
        )
        assertEquals(listOf(olegs.myPress), runBlocking { on.machines.all(ME.userId) })
        assertTrue(stoodIn)
        assertEquals(
            olegs.olegPress.id.value,
            vm.state.value.notice
                ?.key,
        )
    }

    @Test
    fun a_friend_s_machine_without_an_own_one_goes_in_as_a_new_linked_copy() {
        val olegs = OlegsGym()
        val on = olegs.gym
        val vm = viewModel(on)
        var opened: CalendarDay? = null

        vm.planToday(PlacedMachine(olegs.olegRow.id, ofFriend = true)) { opened = it }

        val copy = olegs.ownRow()
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
            PlacementNoticeUi(olegs.olegRow.id.value, "No connection to the server"),
            vm.state.value.notice,
        )
    }

    @Test
    fun a_new_plan_for_a_friend_s_machine_opens_with_the_account_s_machine() {
        val olegs = OlegsGym()
        val vm = viewModel(olegs.gym)
        var opened: List<MachineId>? = null

        vm.choosePlan(PlacedMachine(olegs.olegPress.id, ofFriend = true))
        vm.newPlan(onOpenMachines = { opened = it })

        assertEquals(listOf(olegs.myPress.id), opened)
    }

    /** Oleg's gym with Sasha, Ivan's child, beside Ivan on the device. */
    private fun olegsGymWithSasha(): OlegsGym {
        val olegs = OlegsGym()
        olegs.gym.withChild(childAccount(SASHA, IVAN_SESSION))
        return olegs
    }

    @Test
    fun a_switch_during_a_friend_s_read_leaves_the_plan_as_it_was_and_says_nothing() {
        val olegs = olegsGymWithSasha()
        val on = olegs.gym
        val vm = viewModel(on)
        vm.choosePlan(PlacedMachine(olegs.olegRow.id, ofFriend = true))
        val gate = CompletableDeferred<Unit>()
        on.friends.gate = gate

        vm.addTo(olegs.mine.id)
        runBlocking { on.accounts.switchTo(SASHA.userId) }
        gate.complete(Unit)

        assertEquals(olegs.mine, on.plans.rows.getValue(olegs.mine.id))
        assertEquals(listOf(olegs.myPress), runBlocking { on.machines.all(ME.userId) })
        assertNull(vm.state.value.notice)
    }

    @Test
    fun a_switch_closes_the_choice_and_drops_the_notice() {
        val olegs = olegsGymWithSasha()
        val on = olegs.gym
        val vm = viewModel(on)
        val placed = PlacedMachine(olegs.olegPress.id, ofFriend = true)
        vm.choosePlan(placed)
        vm.addTo(olegs.mine.id)
        vm.choosePlan(placed)

        runBlocking { on.accounts.switchTo(SASHA.userId) }

        assertEquals(PlacementUi(), vm.state.value)
        vm.addTo(olegs.mine.id)
        assertEquals(
            listOf(olegs.myPress.id),
            on.plans.rows
                .getValue(olegs.mine.id)
                .machineIds,
        )
    }

    @Test
    fun a_friend_s_machine_gone_from_the_group_says_so() {
        val olegs = OlegsGym()
        val gone = Machine.new("Hack squat", OLEG.userId, t0)
        val vm = viewModel(olegs.gym)
        var opened: CalendarDay? = null

        vm.planToday(PlacedMachine(gone.id, ofFriend = true)) { opened = it }

        assertNull(opened)
        assertEquals(
            PlacementNoticeUi(gone.id.value, "The machine is no longer available"),
            vm.state.value.notice,
        )
    }

    @Test
    fun a_friend_s_card_goes_into_a_plan_as_the_account_s_machines_and_says_so() {
        val olegs = OlegsGym()
        val vm = viewModel(olegs.gym)

        vm.choosePlan("card", olegs.card)
        vm.addTo(olegs.mine.id)

        val row = olegs.ownRow()
        assertEquals(
            listOf(olegs.myPress.id, row.id),
            olegs.gym.plans.rows
                .getValue(olegs.mine.id)
                .machineIds,
        )
        assertEquals(setOf("back"), row.tags)
        assertTrue(
            olegs.gym.machineLinks.rows.values
                .any { it.machineId == row.id && it.linkedMachineId == olegs.olegRow.id },
        )
        assertEquals(PlacementNoticeUi("card", "Added to “Legs”"), vm.state.value.notice)
    }

    @Test
    fun a_new_plan_for_a_friend_s_card_opens_with_the_account_s_machines() {
        val olegs = OlegsGym()
        val vm = viewModel(olegs.gym)
        var opened: List<MachineId>? = null
        var openedDay: CalendarDay? = null

        vm.choosePlan("card", olegs.card)
        vm.newPlan(onOpen = { openedDay = it }, onOpenMachines = { opened = it })

        assertEquals(listOf(olegs.myPress.id, olegs.ownRow().id), opened)
        assertNull(openedDay)
        assertNull(vm.state.value.choices)
    }

    @Test
    fun a_friend_s_card_repeats_today_as_the_account_s_machines() {
        val olegs = OlegsGym()
        val vm = viewModel(olegs.gym)
        var opened: CalendarDay? = null

        vm.planToday("card", olegs.card) { opened = it }

        assertEquals(olegs.gym.today, opened)
        assertEquals(
            listOf(olegs.myPress.id, olegs.ownRow().id),
            runBlocking { olegs.gym.visits.onDay(ME.userId, olegs.gym.today) }?.planned,
        )
    }

    @Test
    fun offline_a_friend_s_card_goes_nowhere_and_says_so() {
        val olegs = OlegsGym()
        olegs.gym.friends.offline = true
        val vm = viewModel(olegs.gym)
        var opened: CalendarDay? = null

        vm.planToday("card", olegs.card) { opened = it }
        vm.choosePlan("card", olegs.card)
        vm.addTo(olegs.mine.id)

        assertNull(opened)
        assertTrue(
            olegs.gym.visits.rows
                .isEmpty(),
        )
        assertEquals(
            olegs.mine,
            olegs.gym.plans.rows
                .getValue(olegs.mine.id),
        )
        assertEquals(
            PlacementNoticeUi("card", "No connection to the server"),
            vm.state.value.notice,
        )
    }
}
