package monster.greyde.kachalochka.ui.machine

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
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
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

@OptIn(ExperimentalCoroutinesApi::class)
class MachineListViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    private fun viewModel(on: FakeGym = gym) =
        MachineListViewModel(
            on.machines,
            on.currentUser,
            on.accounts,
            on.sync,
            on.friends,
            on.machineLinks,
            on.profiles,
        )

    private fun olegsGym(): Pair<FakeGym, Machine> {
        val on = signedInGym()
        on.friends.group("Зал на Лесной", owner = OLEG, ME)
        val olegPress =
            Machine
                .new("Жим ногами", OLEG.userId, t0)
                .copy(weightMode = WeightMode.PerSide, weightStep = 5.0)
        on.friends.machines += olegPress
        return on to olegPress
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_machines_are_listed_by_name_with_how_they_are_weighed() =
        runTest {
            gym.machines.upsert(Machine.new("Тяга", null, t0))
            gym.machines.upsert(
                Machine
                    .new("Жим ногами", null, t0)
                    .copy(weightMode = WeightMode.PerSide, weightStep = 5.0),
            )
            gym.machines.upsert(Machine.new("Гакк", null, t0).copy(deleted = true))

            val vm = viewModel().also { it.load() }

            assertEquals(
                listOf("Жим ногами" to "кг на сторону · ±5", "Тяга" to "кг всего · ±2.5"),
                vm.state.value.own
                    ?.map { it.name to it.detail },
            )
        }

    @Test
    fun a_machine_s_step_reads_in_the_unit_chosen_in_the_profile() =
        runTest {
            gym.machines.upsert(
                Machine.new("Кроссовер", null, t0).copy(unit = WeightUnit.Lb, weightStep = 5.0),
            )
            val vm = viewModel().also { it.load() }

            assertEquals(
                listOf("кг всего · ±2.3"),
                vm.state.value.own
                    ?.map { it.detail },
            )

            gym.profiles.upsert(Profile.new(null, t0).copy(weightUnit = PreferredWeightUnit.Mixed))
            vm.load()

            assertEquals(
                listOf("lb всего · ±5"),
                vm.state.value.own
                    ?.map { it.detail },
            )
        }

    @Test
    fun a_friend_s_machine_reads_in_the_viewer_s_unit() =
        runTest {
            val (on, _) = olegsGym()
            on.profiles.upsert(
                Profile.new(ME.userId, t0).copy(weightUnit = PreferredWeightUnit.Lb),
            )

            val vm = viewModel(on).also { it.load() }

            assertEquals(
                listOf("Олег · lb на сторону · ±11"),
                vm.state.value.friends
                    .map { it.detail },
            )
        }

    @Test
    fun a_switch_lists_the_other_account_s_machines() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            gym.machines.upsert(Machine.new("Жим ногами", ivan.account.userId, t0))
            gym.machines.upsert(Machine.new("Тяга", misha.account.userId, t0))
            val vm = viewModel().also { it.load() }

            gym.accounts.switchTo(misha.account.userId)

            assertEquals(
                listOf("Тяга"),
                vm.state.value.own
                    ?.map { it.name },
            )
        }

    @Test
    fun a_finished_sync_shows_the_machines_it_pulled() =
        runTest {
            val vm = viewModel().also { it.load() }

            gym.machines.upsert(Machine.new("Тяга", null, t0))
            gym.sync.completePass()

            assertEquals(
                listOf("Тяга"),
                vm.state.value.own
                    ?.map { it.name },
            )
        }

    @Test
    fun own_machines_show_while_the_friends_read_is_still_in_flight() {
        val (on, _) = olegsGym()
        runBlocking { on.machines.upsert(Machine.new("Тяга", ME.userId, t0)) }
        on.friends.gate = CompletableDeferred()

        val vm = viewModel(on).also { it.load() }

        assertEquals(
            listOf("Тяга"),
            vm.state.value.own
                ?.map { it.name },
        )
        assertEquals(emptyList(), vm.state.value.friends)
    }

    @Test
    fun friends_machines_follow_with_their_owner_and_settings() {
        val (on, olegPress) = olegsGym()

        val vm = viewModel(on).also { it.load() }

        assertEquals(
            listOf(
                MachineListRowUi(
                    olegPress.id,
                    "Жим ногами",
                    "Олег · кг на сторону · ±5",
                    friend = OLEG.userId,
                ),
            ),
            vm.state.value.friends,
        )
    }

    @Test
    fun a_friend_s_machine_linked_to_an_own_one_is_not_listed() =
        runTest {
            val (on, olegPress) = olegsGym()
            val mine = Machine.new("Жим", ME.userId, t0)
            on.machines.upsert(mine)
            on.friends.links +=
                MachineLink(MachineLinkId.random(), OLEG.userId, olegPress.id, mine.id, t0, false)

            val vm = viewModel(on).also { it.load() }

            assertEquals(emptyList(), vm.state.value.friends)
        }

    @Test
    fun a_machine_two_friends_linked_is_listed_once_as_the_original() {
        val on = signedInGym()
        on.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        val olegPress = Machine.new("Жим ногами", OLEG.userId, t0)
        val (pashaCopy, link) = linkedCopy(olegPress, PASHA.userId, t0)
        on.friends.machines += listOf(pashaCopy, olegPress)
        on.friends.links += link

        val vm = viewModel(on).also { it.load() }

        assertEquals(
            listOf(olegPress.id),
            vm.state.value.friends
                .map { it.id },
        )
    }

    @Test
    fun offline_the_friends_section_is_absent() {
        val (on, _) = olegsGym()
        runBlocking { on.machines.upsert(Machine.new("Тяга", ME.userId, t0)) }
        on.friends.offline = true

        val vm = viewModel(on).also { it.load() }

        assertEquals(
            listOf("Тяга"),
            vm.state.value.own
                ?.map { it.name },
        )
        assertEquals(emptyList(), vm.state.value.friends)
    }

    @Test
    fun without_an_account_no_friends_are_asked_for() {
        viewModel().load()

        assertEquals(0, gym.friends.reads)
    }

    @Test
    fun friends_read_for_the_previous_account_are_dropped_on_a_switch() =
        runTest {
            val on = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            on.friends.machines += Machine.new("Жим ногами", OLEG.userId, t0)
            val vm = viewModel(on).also { it.load() }
            assertEquals(1, vm.state.value.friends.size)

            on.friends.gate = CompletableDeferred()
            on.accounts.switchTo(misha.account.userId)

            assertEquals(emptyList(), vm.state.value.friends)
        }
}
