package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.identity.InMemoryAccountStorage
import monster.greyde.kachalochka.core.data.identity.OwnerlessRows
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class GetCredentialCancellationException : Exception("the user backed out")

private class FailingSignIn(
    private val error: Throwable,
) : GoogleSignIn {
    override suspend fun signIn(): AccountSession = throw error
}

private class NoOpSessionActivation : SessionActivation {
    override suspend fun activate(session: AccountSession) = Unit

    override suspend fun clear() = Unit
}

private class NoOpOwnerlessRows : OwnerlessRows {
    override suspend fun claim(owner: UserId) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun backing_out_of_the_google_picker_reads_as_a_change_of_mind() {
        assertTrue(isUserCancellation(GetCredentialCancellationException()))
    }

    @Test
    fun a_missing_client_id_is_not_mistaken_for_a_change_of_mind() {
        assertFalse(isUserCancellation(IllegalStateException("Sign-in needs a visible screen")))
    }

    @Test
    fun kotlin_s_own_cancellation_is_not_mistaken_for_a_change_of_mind() {
        assertFalse(isUserCancellation(CancellationException("stopped")))
    }

    private fun viewModel(signIn: GoogleSignIn) =
        AccountsViewModel(
            Accounts(
                PersistedAccountStore(InMemoryAccountStorage()),
                signIn,
                NoOpSessionActivation(),
                NoOpOwnerlessRows(),
            ),
        )

    /**
     * `addAccount()` fires and forgets on `viewModelScope`, so a real failure escapes as an
     * uncaught exception on `Dispatchers.Main` rather than a thrown one; `runTest` is what
     * surfaces that as this test's own failure (or lets it pass quietly), which is the only way
     * to see whether the catch block is actually wired up rather than just its predicate.
     */
    @Test
    fun backing_out_of_the_picker_leaves_no_trace_of_an_error() =
        runTest {
            viewModel(FailingSignIn(GetCredentialCancellationException())).addAccount()
        }

    @Test
    fun a_real_sign_in_failure_is_not_swallowed() {
        val error = IllegalStateException("Sign-in needs a visible screen")
        val propagated =
            assertFailsWith<IllegalStateException> {
                runTest { viewModel(FailingSignIn(error)).addAccount() }
            }
        assertEquals(error.message, propagated.message)
    }
}
