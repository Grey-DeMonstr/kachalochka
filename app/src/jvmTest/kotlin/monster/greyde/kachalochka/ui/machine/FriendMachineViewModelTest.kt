package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class FriendMachineViewModelTest {
    private val on = signedInGym()
    private val t0 = on.clock.current
    private val olegPress =
        Machine
            .new("Жим ногами", OLEG.userId, t0)
            .copy(
                setupNote = "Спинка на 4",
                weightMode = WeightMode.PerSide,
                weightStep = 5.0,
                platformWeight = 25.0,
            )

    init {
        on.friends.group("Зал на Лесной", owner = OLEG, ME)
        on.friends.machines += olegPress
    }

    private fun viewModel(gym: FakeGym = on) =
        FriendMachineViewModel(
            olegPress.id,
            OLEG.userId,
            gym.friends,
            gym.catalogue,
            gym.currentUser,
            gym.accounts,
            gym.profiles,
            gym.clock,
            gym.utcOffset,
        )

    /** One of Oleg's visits on his press, [daysAgo], as sets of weight to reps. */
    private fun olegTrained(
        daysAgo: Int,
        vararg sets: Pair<Double, Int>,
    ) {
        val visit = VisitId.random()
        on.friends.sets +=
            sets.mapIndexed { i, (weight, reps) ->
                WorkoutSet(
                    WorkoutSetId.random(),
                    OLEG.userId,
                    visit,
                    olegPress.id,
                    weight,
                    reps,
                    i + 1,
                    t0 - daysAgo.days + i.minutes,
                    t0,
                    false,
                )
            }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_friend_s_settings_are_shown() {
        val vm = viewModel()

        val state = assertNotNull(vm.state.value)
        assertEquals(
            listOf("Жим ногами", "Олег", "Спинка на 4", "кг на сторону · ±5"),
            listOf(state.name, state.owner, state.note, state.caption),
        )
        assertEquals("25 кг · рядом с названием", state.platform)
        assertTrue(state.canTake)
        assertFalse(vm.offline.value)
    }

    @Test
    fun the_friend_s_tags_are_shown_in_their_order() {
        on.friends.machines.clear()
        on.friends.machines += olegPress.copy(tags = setOf("Ноги", "База"))

        assertEquals(listOf("База", "Ноги"), viewModel().state.value?.tags)
    }

    @Test
    fun the_photo_the_friend_chose_is_the_cover_else_the_first() {
        val first = Photo.new(olegPress.id, OLEG.userId, t0)
        val second = Photo.new(olegPress.id, OLEG.userId, t0 + 1.minutes)
        on.friends.photos += listOf(first, second)
        on.friends.machines.clear()
        on.friends.machines += olegPress.copy(coverPhoto = second.id)

        assertEquals(
            listOf(first.id.value to false, second.id.value to true),
            viewModel()
                .state.value
                ?.photos
                ?.map { it.key to it.cover },
        )

        on.friends.machines.clear()
        on.friends.machines += olegPress
        assertEquals(
            listOf(true, false),
            viewModel()
                .state.value
                ?.photos
                ?.map { it.cover },
        )
    }

    @Test
    fun the_friend_s_statistics_on_the_machine_read_as_the_viewer_s_own_would() {
        olegTrained(40, 70.0 to 10)
        olegTrained(1, 80.0 to 8, 85.0 to 6)
        val vm = viewModel()

        val month = assertNotNull(vm.state.value).stats
        assertEquals("85 кг × 6", month.best)
        assertEquals(listOf(on.today.plusDays(-1) to 85.0), month.points)
        assertEquals(
            listOf("13 ноября" to "80-85кг на каждую, 8-6", "5 октября" to "70кг на каждую, 1x10"),
            month.history.map { it.date to it.results },
        )

        val reads = on.friends.reads
        vm.choosePeriod(StatsPeriod.ThreeMonths)

        val quarter = assertNotNull(vm.state.value)
        assertEquals(StatsPeriod.ThreeMonths, quarter.period)
        assertEquals(on.today.minusMonths(3), quarter.stats.start)
        assertEquals(2, quarter.stats.points.size)
        // The period is chosen over what was read: no second round trip.
        assertEquals(reads, on.friends.reads)
    }

    @Test
    fun the_friend_s_statistics_read_in_the_viewer_s_unit() =
        runTest {
            olegTrained(1, 80.0 to 8, 85.0 to 6)
            on.profiles.upsert(
                Profile.new(ME.userId, t0).copy(weightUnit = PreferredWeightUnit.Lb),
            )

            val stats = assertNotNull(viewModel().state.value).stats

            assertEquals("187.5 lb × 6", stats.best)
            assertEquals("176.5-187.5lb на каждую, 8-6", stats.history.single().results)
        }

    @Test
    fun the_friend_s_settings_keep_the_machine_s_own_unit() =
        runTest {
            on.profiles.upsert(
                Profile.new(ME.userId, t0).copy(weightUnit = PreferredWeightUnit.Lb),
            )

            val state = assertNotNull(viewModel().state.value)

            assertEquals("кг на сторону · ±5", state.caption)
            assertEquals("25 кг · рядом с названием", state.platform)
        }

    @Test
    fun a_friend_s_machine_in_pounds_reads_in_pounds_for_a_viewer_in_kilograms() {
        on.friends.machines.clear()
        on.friends.machines +=
            olegPress.copy(unit = WeightUnit.Lb, weightStep = 5.0, platformWeight = 45.0)

        val state = assertNotNull(viewModel().state.value)

        assertEquals("lb на сторону · ±5", state.caption)
        assertEquals("45 lb · рядом с названием", state.platform)
    }

    @Test
    fun a_platform_added_to_the_record_says_so_and_none_shows_nothing() {
        on.friends.machines.clear()
        on.friends.machines += olegPress.copy(platformIncluded = true)
        assertEquals("25 кг · прибавляется к записи", viewModel().state.value?.platform)

        on.friends.machines.clear()
        on.friends.machines += olegPress.copy(platformWeight = 0.0)
        assertNull(viewModel().state.value?.platform)
    }

    @Test
    fun taking_it_saves_a_linked_copy_and_hands_it_on() =
        runTest {
            val vm = viewModel()
            var taken: MachineId? = null

            vm.take { taken = it }

            val copy = assertNotNull(on.machines.byId(assertNotNull(taken)))
            assertEquals(ME.userId to 5.0, copy.userId to copy.weightStep)
            val link =
                on.machineLinks.rows.values
                    .single()
            assertEquals(
                Triple(ME.userId, copy.id, olegPress.id),
                Triple(link.userId, link.machineId, link.linkedMachineId),
            )
            assertEquals(1, on.sync.requests)
        }

    @Test
    fun a_machine_the_account_already_has_linked_cannot_be_taken_again() =
        runTest {
            val mine = Machine.new("Жим", ME.userId, t0)
            on.machines.upsert(mine)
            on.machineLinks.upsert(
                MachineLink(MachineLinkId.random(), ME.userId, mine.id, olegPress.id, t0, false),
            )
            val vm = viewModel()

            assertEquals(false, vm.state.value?.canTake)
            vm.take {}
            assertEquals(listOf(mine), on.machines.all(ME.userId))
        }

    @Test
    fun a_machine_linked_to_the_account_s_through_a_friend_cannot_be_taken_either() =
        runTest {
            val mine = Machine.new("Жим", ME.userId, t0)
            val pashaPress = Machine.new("Жим ногами", PASHA.userId, t0)
            on.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
            on.friends.machines += pashaPress
            on.machines.upsert(mine)
            on.machineLinks.upsert(
                MachineLink(MachineLinkId.random(), ME.userId, mine.id, pashaPress.id, t0, false),
            )
            on.friends.links +=
                MachineLink(
                    MachineLinkId.random(),
                    PASHA.userId,
                    pashaPress.id,
                    olegPress.id,
                    t0,
                    false,
                )

            assertEquals(false, viewModel().state.value?.canTake)
        }

    @Test
    fun offline_it_says_so_and_takes_nothing() {
        on.friends.offline = true
        val vm = viewModel()
        var taken: MachineId? = null

        vm.take { taken = it }

        assertTrue(vm.offline.value)
        assertNull(vm.state.value)
        assertNull(taken)
        assertTrue(on.machineLinks.rows.isEmpty())
    }

    @Test
    fun a_retry_shows_it_once_the_network_answers() {
        on.friends.offline = true
        val vm = viewModel()

        on.friends.offline = false
        vm.refresh()

        assertFalse(vm.offline.value)
        assertEquals("Жим ногами", vm.state.value?.name)
    }

    @Test
    fun a_switch_to_an_account_outside_the_group_takes_nothing() =
        runTest {
            val misha =
                AccountSession(
                    Account(
                        UserId("22222222-2222-4222-8222-222222222222"),
                        "misha@example.test",
                        "Миша",
                    ),
                    "access",
                    "refresh",
                    t0,
                )
            val gym = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friends.machines += olegPress
            val vm = viewModel(gym)

            gym.accounts.switchTo(misha.account.userId)
            vm.take {}

            assertNull(vm.state.value)
            assertEquals(emptyList(), gym.machines.all(misha.account.userId))
        }
}
