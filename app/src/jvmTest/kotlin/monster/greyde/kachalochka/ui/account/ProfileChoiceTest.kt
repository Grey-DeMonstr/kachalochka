package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.fakes.FakeGym
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
    private val unsaved = UnsavedChoices()

    private fun choice(
        gym: FakeGym,
        scope: CoroutineScope,
    ) = machineSortChoice(gym.profiles, gym.currentUser, gym.clock, gym.sync, unsaved, scope)

    @Test
    fun an_account_s_choice_is_kept_in_its_profile() =
        runTest {
            val gym = signedInGym()
            val choice = choice(gym, this)

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
            val choice = choice(gym, this)

            choice.choose(MachineSort.Name)
            advanceUntilIdle()

            assertEquals(MachineSort.Name, choice.current)
            assertNull(gym.profiles.forOwner(SASHA.userId))
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_managed_child_s_choice_outlives_a_reload_and_reaches_other_screens() =
        runTest {
            val gym = signedInGym().withChild(childAccount(SASHA, IVAN_SESSION))
            gym.accounts.switchTo(SASHA.userId)
            val choice = choice(gym, this)
            choice.choose(MachineSort.Name)
            advanceUntilIdle()

            choice.read(SASHA.userId)
            val another = choice(gym, this).also { it.read(SASHA.userId) }

            assertEquals(MachineSort.Name, choice.current)
            assertEquals(MachineSort.Name, another.current)
        }

    @Test
    fun a_managed_child_s_choice_stays_with_the_child() =
        runTest {
            val gym = signedInGym().withChild(childAccount(SASHA, IVAN_SESSION))
            gym.accounts.switchTo(SASHA.userId)
            val choice = choice(gym, this)
            choice.choose(MachineSort.Name)
            advanceUntilIdle()

            gym.accounts.switchTo(IVAN_SESSION.account.userId)
            choice.read(IVAN_SESSION.account.userId)

            assertEquals(MachineSort.Recent, choice.current)
        }
}
