package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.domain.identity.UserId

class Accounts(
    private val store: AccountStore,
    private val signIn: GoogleSignIn,
    private val sessions: SessionActivation,
    private val ownerless: OwnerlessRows,
) {
    private val failure = MutableStateFlow<String?>(null)

    val accounts: StateFlow<List<Account>> get() = store.accounts
    val activeId: StateFlow<UserId?> get() = store.activeId

    /** Why the last attempt failed; null while none has. */
    val lastFailure: StateFlow<String?> = failure

    suspend fun addAccount() {
        failure.value = null
        reporting {
            val first = store.accounts.value.isEmpty()
            val session = signIn.signIn()
            store.add(session)
            sessions.activate(session)
            if (first) ownerless.claim(session.account.userId)
        }
    }

    // The store moves only once the session is live, so the avatar can never name an account the
    // app is not talking to the server as.
    suspend fun switchTo(id: UserId) {
        val session = store.sessionOf(id) ?: return
        reporting {
            sessions.activate(session)
            store.switch(id)
        }
    }

    suspend fun signOut(id: UserId) {
        store.remove(id)
        val next = store.activeId.value?.let { store.sessionOf(it) }
        reporting { if (next == null) sessions.clear() else sessions.activate(next) }
    }

    /**
     * Google, the network and Supabase can each refuse, and every caller is a fire-and-forget
     * `launch`, where a throw lands on the main thread as an uncaught exception. A refusal is
     * recorded for the surface that offered the action instead.
     */
    private suspend fun reporting(work: suspend () -> Unit) {
        try {
            work()
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (refused: Exception) {
            if (!isUserCancellation(refused)) failure.value = refused.toString()
        }
    }
}

/**
 * Android's picker throws `GetCredentialCancellationException` when the user backs out; that type
 * lives in androidx.credentials, invisible from commonMain, so its class name is the only
 * cross-platform way to tell a change of mind from a broken sign-in.
 */
internal fun isUserCancellation(error: Throwable): Boolean =
    error::class.simpleName == "GetCredentialCancellationException"
