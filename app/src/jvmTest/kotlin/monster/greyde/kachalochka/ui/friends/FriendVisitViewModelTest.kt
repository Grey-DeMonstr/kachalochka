package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class FriendVisitViewModelTest {
    private val gym = signedInGym()
    private val t0 = gym.clock.current
    private val yesterday = gym.today.plusDays(-1)
    private val myPress = Machine.new("Жим ногами", ME.userId, t0)
    private val olegPress = linkedCopy(myPress, OLEG.userId, t0).copy(name = "Платформа")
    private val olegRow = Machine.new("Тяга", OLEG.userId, t0).copy(unit = WeightUnit.Lb)
    private val olegVisit = Visit(VisitId.random(), OLEG.userId, yesterday, t0 - 1.days, t0, false)

    private fun olegSet(
        machine: Machine,
        weight: Double,
        reps: Int,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        OLEG.userId,
        olegVisit.id,
        machine.id,
        weight,
        reps,
        0,
        t0 - 1.days + minutes.minutes,
        t0,
        false,
    )

    private fun viewModel(day: CalendarDay = yesterday) =
        FriendVisitViewModel(
            OLEG.userId,
            "Олег",
            day,
            gym.friends,
            gym.machines,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.utcOffset,
        )

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friends.machines += listOf(olegPress, olegRow)
            gym.friends.visits += olegVisit
            gym.friends.sets +=
                listOf(
                    olegSet(olegPress, 80.0, 8, 0),
                    olegSet(olegPress, 85.0, 6, 1),
                    olegSet(olegRow, 100.0, 10, 2),
                )
            gym.machines.upsert(myPress)
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_friend_s_visit_groups_their_sets_under_the_viewer_s_names_where_linked() {
        val state = assertNotNull(viewModel().state.value)

        assertEquals("Олег · 13 ноября", state.title)
        assertEquals("3 подхода", state.setCountLabel)
        assertEquals(
            listOf("Жим ногами" to "80, 85 кг", "Тяга" to "100 lb"),
            state.groups.map { it.title to it.summary },
        )
        assertEquals(
            listOf("Жим ногами · подход 1" to "80 кг × 8", "Жим ногами · подход 2" to "85 кг × 6"),
            state.groups
                .first()
                .sets
                .map { it.title to it.value },
        )
    }

    @Test
    fun a_day_without_their_visit_shows_no_sets() {
        val state = assertNotNull(viewModel(day = gym.today).state.value)

        assertEquals(emptyList(), state.groups)
        assertEquals("0 подходов", state.setCountLabel)
    }

    @Test
    fun offline_the_visit_says_so_and_a_retry_reads_it_again() {
        gym.friends.offline = true
        val vm = viewModel()
        assertTrue(vm.offline.value)

        gym.friends.offline = false
        vm.refresh()

        assertFalse(vm.offline.value)
        assertEquals(
            2,
            vm.state.value
                ?.groups
                ?.size,
        )
    }
}
