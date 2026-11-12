package monster.greyde.kachalochka.ui.plans

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
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class VisitPlacementViewModelTest {
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
            gym.visits.upsert(visit)
            gym.sets.upsert(set(press, 0))
            gym.sets.upsert(set(row, 1))
        }
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(on: FakeGym = gym) =
        VisitPlacementViewModel(on.placement, on.accounts, on.catalogue)

    @Test
    fun the_choice_lists_every_plan_by_its_title() {
        val vm = viewModel()

        vm.choosePlan(gym.today)

        assertEquals(listOf(PlanChoiceUi(legs.id, "Legs")), vm.state.value.choices)
    }

    @Test
    fun a_chosen_plan_takes_the_visit_s_machines_it_lacks_at_its_end_and_says_so() {
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
            VisitPlacementNoticeUi(gym.today.iso, "Added to “Legs”"),
            vm.state.value.notice,
        )
    }

    @Test
    fun a_new_plan_opens_for_the_visit_s_day() {
        val vm = viewModel()
        var opened: CalendarDay? = null

        vm.choosePlan(gym.today)
        vm.newPlan { opened = it }

        assertEquals(gym.today, opened)
        assertNull(vm.state.value.choices)
    }

    @Test
    fun a_choice_made_before_a_switch_writes_nothing() {
        val vm = viewModel()
        vm.choosePlan(gym.today)
        gym.withAccounts(IVAN_SESSION, active = IVAN_SESSION)

        vm.addTo(legs.id)

        assertEquals(legs, gym.plans.rows.getValue(legs.id))
        assertNull(vm.state.value.notice)
    }

    /** Ivan's press, with Oleg's linked copy of it, and Oleg's row, linked to nothing. */
    private class OlegsGym {
        val gym = signedInGym()
        val myPress = Machine.new("Leg press", ME.userId, gym.clock.current)
        private val olegCopy = linkedCopy(myPress, OLEG.userId, gym.clock.current)
        val olegPress = olegCopy.first
        val olegRow =
            Machine.new("Row", OLEG.userId, gym.clock.current).copy(tags = setOf("back"))
        val card = listOf(olegPress.id, olegRow.id)
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
        assertEquals(VisitPlacementNoticeUi("card", "Added to “Legs”"), vm.state.value.notice)
    }

    @Test
    fun a_new_plan_for_a_friend_s_card_opens_with_the_account_s_machines() {
        val olegs = OlegsGym()
        val vm = viewModel(olegs.gym)
        var opened: List<MachineId>? = null
        var openedDay: CalendarDay? = null

        vm.choosePlan("card", olegs.card)
        vm.newPlan(onOpenMachines = { opened = it }) { openedDay = it }

        assertEquals(listOf(olegs.myPress.id, olegs.ownRow().id), opened)
        assertNull(openedDay)
        assertNull(vm.state.value.choices)
    }

    @Test
    fun a_friend_s_card_repeats_today_as_the_account_s_machines() {
        val olegs = OlegsGym()
        val vm = viewModel(olegs.gym)
        var opened: CalendarDay? = null

        vm.repeatToday("card", olegs.card) { opened = it }

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

        vm.repeatToday("card", olegs.card) { opened = it }
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
            VisitPlacementNoticeUi("card", "No connection to the server"),
            vm.state.value.notice,
        )
    }
}
