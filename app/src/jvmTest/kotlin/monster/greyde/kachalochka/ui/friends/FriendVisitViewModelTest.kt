package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.ui.machine.LinkedMachineUi
import monster.greyde.kachalochka.ui.strings.inEnglish
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
    private val fixture = OlegVisitFixture(gym)

    private fun viewModel(day: CalendarDay = fixture.yesterday) =
        FriendVisitViewModel(
            OLEG.userId,
            "Олег",
            day,
            gym.friends,
            gym.catalogue,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.utcOffset,
            gym.profiles,
        )

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            fixture.install()
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_friend_s_visit_lists_their_machines_under_their_own_names() {
        val state = assertNotNull(viewModel().state.value)

        assertEquals("Олег", state.title)
        assertEquals("Понедельник, 13 ноября", state.day)
        assertEquals("2 упражнения", state.countLabel)
        assertEquals(
            listOf(
                "Платформа" to listOf("80-85кг", "8-6"),
                "Тяга" to listOf("45.5кг", "1x10"),
            ),
            state.groups.map { it.title to it.summary },
        )
    }

    @Test
    fun a_friend_s_machine_names_the_viewer_s_and_other_friends_machines_linked_to_it() =
        runTest {
            gym.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
            val pashas = Machine.new("Жим", PASHA.userId, gym.clock.current)
            gym.friends.machines += pashas
            gym.friends.links +=
                MachineLink(
                    MachineLinkId.random(),
                    PASHA.userId,
                    pashas.id,
                    fixture.olegPress.id,
                    gym.clock.current,
                    deleted = false,
                )

            val state = assertNotNull(viewModel().state.value)

            assertEquals(
                listOf(
                    listOf(
                        LinkedMachineUi(fixture.myPress.id, ME, "Жим ногами", own = true),
                        LinkedMachineUi(pashas.id, PASHA, "Жим"),
                    ),
                    emptyList(),
                ),
                state.groups.map { it.linkedWith },
            )
        }

    @Test
    fun coming_back_in_another_language_reads_the_visit_again() {
        val vm = viewModel()
        val reads = gym.friends.reads

        vm.speak()
        assertEquals(reads, gym.friends.reads)

        inEnglish {
            vm.speak()
            assertEquals("Monday, 13 November", vm.state.value?.day)
        }
    }

    @Test
    fun a_friend_s_sets_read_in_the_viewer_s_unit() =
        runTest {
            gym.profiles.upsert(
                Profile
                    .new(ME.userId, gym.clock.current)
                    .copy(weightUnit = PreferredWeightUnit.Lb),
            )

            val state = assertNotNull(viewModel().state.value)

            assertEquals(
                listOf(listOf("176.5-187.5lb", "8-6"), listOf("100lb", "1x10")),
                state.groups.map { it.summary },
            )
        }

    @Test
    fun a_friend_s_machine_the_viewer_linked_to_names_the_viewer_s_machine() =
        runTest {
            val myRow = Machine.new("Тяга верхнего блока", ME.userId, gym.clock.current)
            gym.machines.upsert(myRow)
            gym.machineLinks.upsert(
                MachineLink(
                    MachineLinkId.random(),
                    ME.userId,
                    myRow.id,
                    fixture.olegRow.id,
                    gym.clock.current,
                    deleted = false,
                ),
            )

            val state = assertNotNull(viewModel().state.value)

            assertEquals(
                listOf("Тяга" to listOf(LinkedMachineUi(myRow.id, ME, myRow.name, own = true))),
                state.groups.drop(1).map { it.title to it.linkedWith },
            )
        }

    @Test
    fun a_day_without_their_visit_shows_no_sets() {
        val state = assertNotNull(viewModel(day = gym.today).state.value)

        assertEquals(emptyList(), state.groups)
        assertEquals("0 упражнений", state.countLabel)
    }

    @Test
    fun an_empty_later_visit_does_not_hide_the_earlier_one_s_sets() {
        val emptyLater =
            Visit(
                VisitId.random(),
                OLEG.userId,
                fixture.yesterday,
                fixture.olegVisit.recordedAt + 5.minutes,
                fixture.olegVisit.recordedAt + 5.minutes,
                false,
            )
        gym.friends.visits += emptyLater

        val state = assertNotNull(viewModel().state.value)

        assertEquals("2 упражнения", state.countLabel)
        assertEquals(2, state.groups.size)
        // The visits, each visit's sets once, the machines, the group's machines, links and
        // photos, and the mates.
        assertEquals(8, gym.friends.reads)
    }

    @Test
    fun a_visit_no_client_has_dated_shows_on_the_day_it_was_recorded_on() {
        val recorded = gym.clock.current - 3.days
        val undated = Visit(VisitId.random(), OLEG.userId, null, recorded, recorded, false)
        gym.friends.visits += undated
        gym.friends.sets +=
            fixture.sets
                .first()
                .copy(id = WorkoutSetId.random(), visitId = undated.id, recordedAt = recorded)

        val state = assertNotNull(viewModel(day = gym.today.plusDays(-3)).state.value)

        assertEquals("1 упражнение", state.countLabel)
    }

    @Test
    fun a_refresh_drops_the_load_it_replaces() {
        val first = CompletableDeferred<Unit>()
        gym.friends.gate = first
        val vm = viewModel()
        gym.friends.gate = null
        vm.refresh()

        gym.friends.offline = true
        first.complete(Unit)

        assertFalse(vm.offline.value)
        assertEquals("2 упражнения", vm.state.value?.countLabel)
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
