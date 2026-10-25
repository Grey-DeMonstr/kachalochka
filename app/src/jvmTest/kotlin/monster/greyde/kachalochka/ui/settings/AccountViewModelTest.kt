package monster.greyde.kachalochka.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван", t0)
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша", t0)

    private fun viewModel() = AccountViewModel(gym.accounts, gym.currentUser, gym.deletion)

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_screen_shows_the_active_account_s_email() =
        runTest {
            gym.withAccounts(ivan, misha, active = misha)

            assertEquals("Миша@example.test", viewModel().email.value)
        }

    @Test
    fun nobody_signed_in_or_a_managed_child_has_no_account_here() =
        runTest {
            assertNull(viewModel().email.value)

            gym.withAccounts(ivan, active = ivan).withChild(childAccount(SASHA, ivan))
            gym.accounts.switchTo(SASHA.userId)

            assertNull(viewModel().email.value)
        }

    @Test
    fun signing_out_takes_the_account_off_the_device() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val vm = viewModel()

            vm.signOut()

            assertEquals(
                listOf(misha.account.userId),
                gym.accounts.accounts.value
                    .map { it.userId },
            )
            assertEquals("Миша@example.test", vm.email.value)
        }

    @Test
    fun a_confirmed_deletion_deletes_the_account_and_signs_it_out() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val vm = viewModel()

            vm.askToDelete()
            assertTrue(vm.deletion.value.confirming)
            vm.typeDeleteWord(" delete ")
            vm.confirmDelete()

            assertEquals(listOf(ivan.account.userId), gym.accountServer.deleted)
            assertEquals(
                listOf(misha.account.userId),
                gym.accounts.accounts.value
                    .map { it.userId },
            )
            assertFalse(vm.deletion.value.confirming)
            assertNull(vm.deletion.value.error)
        }

    @Test
    fun offline_a_deletion_says_so_and_keeps_the_account() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.accountServer.offline = true
            val vm = viewModel()

            vm.askToDelete()
            vm.typeDeleteWord("DELETE")
            vm.confirmDelete()

            assertEquals("Нет связи с сервером", vm.deletion.value.error)
            assertEquals(ivan.account.userId, gym.accounts.activeId.value)
            assertFalse(vm.deletion.value.running)
        }

    @Test
    fun cancelling_keeps_the_account() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.askToDelete()
            vm.cancelDelete()

            assertFalse(vm.deletion.value.confirming)
            assertEquals(emptyList(), gym.accountServer.deleted)
        }

    @Test
    fun a_deletion_needs_the_word_typed() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.askToDelete()
            vm.typeDeleteWord("DELET")
            assertFalse(vm.deletion.value.canConfirm)
            vm.confirmDelete()

            assertEquals(emptyList(), gym.accountServer.deleted)
            vm.typeDeleteWord("DELETE")
            assertTrue(vm.deletion.value.canConfirm)
        }
}
