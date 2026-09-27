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
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
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

    private fun viewModel() =
        MachineListViewModel(gym.machines, gym.currentUser, gym.accounts, gym.sync)

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
                listOf("Жим ногами" to "кг на сторону · ±5", "Тяга" to "кг всего · ±2,5"),
                vm.state.value?.map { it.name to it.detail },
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

            assertEquals(listOf("Тяга"), vm.state.value?.map { it.name })
        }

    @Test
    fun a_finished_sync_shows_the_machines_it_pulled() =
        runTest {
            val vm = viewModel().also { it.load() }

            gym.machines.upsert(Machine.new("Тяга", null, t0))
            gym.sync.completePass()

            assertEquals(listOf("Тяга"), vm.state.value?.map { it.name })
        }
}
