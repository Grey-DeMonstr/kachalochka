package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.signedInGym
import monster.greyde.kachalochka.ui.machine.machineSortChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileChoiceTest {
    @Test
    fun an_account_s_choice_is_kept_in_its_profile() =
        runTest {
            val gym = signedInGym()
            val choice =
                machineSortChoice(gym.profiles, gym.currentUser, gym.clock, gym.sync, this)

            choice.choose(MachineSort.Name)
            advanceUntilIdle()

            assertEquals(
                MachineSort.Name,
                gym.profiles.forOwner(IVAN_SESSION.account.userId)?.machineSort,
            )
        }

    @Test
    fun a_managed_child_s_choice_holds_without_touching_any_profile() =
        runTest {
            val gym = signedInGym().withChild(childAccount(SASHA, IVAN_SESSION))
            gym.accounts.switchTo(SASHA.userId)
            val choice =
                machineSortChoice(gym.profiles, gym.currentUser, gym.clock, gym.sync, this)

            choice.choose(MachineSort.Name)
            advanceUntilIdle()

            assertEquals(MachineSort.Name, choice.current)
            assertNull(gym.profiles.forOwner(SASHA.userId))
            assertEquals(0, gym.sync.requests)
        }
}
