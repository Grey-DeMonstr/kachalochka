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
import monster.greyde.kachalochka.core.domain.gym.WeightMode
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
            gym.machines,
            gym.machineLinks,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.sync,
            gym.profiles,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_friend_s_settings_are_shown() {
        val vm = viewModel()

        assertEquals(
            FriendMachineUi(
                name = "Жим ногами",
                owner = "Олег",
                note = "Спинка на 4",
                caption = "кг на сторону · ±5",
                platform = "25 кг · рядом с названием",
                canTake = true,
            ),
            vm.state.value,
        )
        assertFalse(vm.offline.value)
    }

    @Test
    fun the_friend_s_weights_read_in_the_viewer_s_unit() =
        runTest {
            on.profiles.upsert(
                Profile.new(ME.userId, t0).copy(weightUnit = PreferredWeightUnit.Lb),
            )

            val state = assertNotNull(viewModel().state.value)

            assertEquals("lb на сторону · ±11", state.caption)
            assertEquals("55 lb · рядом с названием", state.platform)
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
