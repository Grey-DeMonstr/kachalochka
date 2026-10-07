package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MineChooserViewModelTest {
    private val gym = signedInGym()
    private val t0 = gym.clock.current
    private val olegs = Machine.new("Жим ногами", OLEG.userId, t0)
    private val press = Machine.new("Жим ногами", ME.userId, t0)
    private val legPress = Machine.new("Жим ногами в тренажёре", ME.userId, t0)
    private val row = Machine.new("Тяга", ME.userId, t0)

    private fun viewModel() =
        MineChooserViewModel(
            olegs.id,
            olegs.name,
            gym.machineLinks,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.sync,
            gym.profiles,
            gym.catalogue,
        ).also { it.load() }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        gym.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        gym.friends.machines += olegs
        runBlocking { listOf(press, legPress, row).forEach { gym.machines.upsert(it) } }
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_search_starts_with_the_friend_s_machine_name_and_lists_only_own_machines() {
        val vm = viewModel()

        assertEquals("Жим ногами", vm.state.value.query)
        assertEquals(
            listOf(press.id, legPress.id),
            vm.state.value.own
                .map { it.id },
        )

        vm.onQueryChange("")

        assertEquals(
            listOf(press.id, legPress.id, row.id),
            vm.state.value.own
                .map { it.id },
        )
    }

    @Test
    fun an_own_machine_already_joined_with_it_is_not_offered() {
        val pashas = Machine.new("Жим", PASHA.userId, t0)
        gym.friends.machines += pashas
        gym.friends.links +=
            MachineLink(MachineLinkId.random(), PASHA.userId, pashas.id, olegs.id, t0, false)
        runBlocking {
            gym.machineLinks.upsert(
                MachineLink(MachineLinkId.random(), ME.userId, legPress.id, pashas.id, t0, false),
            )
        }

        val vm = viewModel()

        assertEquals(
            listOf(press.id),
            vm.state.value.own
                .map { it.id },
        )
    }

    @Test
    fun choosing_an_own_machine_links_it_to_the_friend_s() {
        val vm = viewModel()
        var done = false

        vm.choose(row.id) { done = true }

        assertTrue(done)
        val link =
            gym.machineLinks.rows.values
                .single()
        assertEquals(
            Triple(ME.userId, row.id, olegs.id),
            Triple(link.userId, link.machineId, link.linkedMachineId),
        )
        assertEquals(1, gym.sync.requests)
    }
}
