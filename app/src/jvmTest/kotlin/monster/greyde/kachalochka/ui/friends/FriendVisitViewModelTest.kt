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
    fun a_friend_s_visit_groups_their_sets_under_the_viewer_s_names_where_linked() {
        val state = assertNotNull(viewModel().state.value)

        assertEquals("Олег · 13 ноября", state.title)
        assertEquals("3 подхода", state.setCountLabel)
        assertEquals(
            listOf("Жим ногами" to "80-85кг 8-6", "Тяга" to "45.5кг 1x10"),
            state.groups.map { it.title to it.summary },
        )
        assertEquals(
            listOf("#1" to "80 кг × 8", "#2" to "85 кг × 6"),
            state.groups
                .first()
                .sets
                .map { it.title to it.value },
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
            assertEquals("Олег · 13 November", vm.state.value?.title)
        }
    }

    @Test
    fun a_friend_s_set_shows_its_comment() {
        gym.friends.sets[0] = gym.friends.sets[0].copy(comment = "Тяжело")

        val state = assertNotNull(viewModel().state.value)

        assertEquals(
            listOf("Тяжело"),
            state.groups
                .flatMap {
                    it.sets
                }.map { it.comment }
                .filter { it.isNotEmpty() },
        )
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
                listOf("176.5-187.5lb 8-6", "100lb 1x10"),
                state.groups.map { it.summary },
            )
            assertEquals(
                listOf("176.5 lb × 8", "187.5 lb × 6", "100 lb × 10"),
                state.groups.flatMap { group -> group.sets.map { it.value } },
            )
        }

    @Test
    fun a_friend_s_machine_the_viewer_linked_to_reads_under_the_viewer_s_name() =
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
                listOf("Жим ногами", "Тяга верхнего блока"),
                state.groups.map { it.title },
            )
        }

    @Test
    fun a_day_without_their_visit_shows_no_sets() {
        val state = assertNotNull(viewModel(day = gym.today).state.value)

        assertEquals(emptyList(), state.groups)
        assertEquals("0 подходов", state.setCountLabel)
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

        assertEquals("3 подхода", state.setCountLabel)
        assertEquals(2, state.groups.size)
        // The visits, each visit's sets once, the machines, the links.
        assertEquals(5, gym.friends.reads)
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

        assertEquals("1 подход", state.setCountLabel)
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
        assertEquals("3 подхода", vm.state.value?.setCountLabel)
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
