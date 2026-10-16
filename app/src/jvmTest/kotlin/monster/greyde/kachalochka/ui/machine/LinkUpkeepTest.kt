package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LinkUpkeepTest {
    private val gym = signedInGym()
    private val t0 = gym.clock.current
    private val myPress = Machine.new("Жим ногами", ME.userId, t0)
    private val olegCopy = linkedCopy(myPress, OLEG.userId, t0)
    private val olegPress = olegCopy.first
    private val olegOther = Machine.new("Жим", OLEG.userId, t0)

    /** Олег linked his press to Иван's, and his other press to his first. */
    private suspend fun olegsLinks() {
        gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        gym.friends.machines += listOf(olegPress, olegOther)
        gym.friends.links += olegCopy.second
        gym.friends.links +=
            MachineLink(MachineLinkId.random(), OLEG.userId, olegOther.id, olegPress.id, t0, false)
        gym.machines.upsert(myPress)
    }

    private fun ownPairs() =
        gym.machineLinks.rows.values
            .filter { !it.deleted }
            .map { it.machineId to it.linkedMachineId }

    @Test
    fun the_active_account_links_its_machines_to_the_whole_cluster_at_start() =
        runTest(UnconfinedTestDispatcher()) {
            olegsLinks()

            backgroundScope.launch { LinkUpkeep(gym.catalogue, gym.accounts).run() }
            runCurrent()

            assertEquals(listOf(myPress.id to olegOther.id), ownPairs())
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_failed_read_waits_for_the_next_start_or_switch() =
        runTest(UnconfinedTestDispatcher()) {
            olegsLinks()
            gym.friends.offline = true

            backgroundScope.launch { LinkUpkeep(gym.catalogue, gym.accounts).run() }
            runCurrent()

            assertEquals(emptyList(), ownPairs())
        }
}
