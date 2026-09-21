package monster.greyde.kachalochka.ui.account

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
     * `addAccount()` fires and forgets on `viewModelScope`, so a failure escapes as an uncaught
     * exception on `Dispatchers.Main` rather than a thrown one; `runTest` is what surfaces that
     * as this test's own failure instead of letting it pass quietly.
     */
    @Test
    fun a_failed_sign_in_says_so_instead_of_crashing_the_app() =
        runTest {
            val viewModel =
                viewModel(FailingSignIn(IllegalStateException("Sign-in needs a visible screen")))

            viewModel.addAccount()

            assertNotNull(viewModel.state.value.failure)
        }

    @Test
    fun backing_out_of_the_picker_leaves_no_trace_of_an_error() =
        runTest {
            val viewModel = viewModel(FailingSignIn(GetCredentialCancellationException()))

            viewModel.addAccount()

            assertNull(viewModel.state.value.failure)
        }
}
