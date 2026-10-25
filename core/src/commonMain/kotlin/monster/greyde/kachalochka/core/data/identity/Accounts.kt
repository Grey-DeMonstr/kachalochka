package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * The device's accounts and the active one among them. Every change of the active account runs
 * the [HeldWrites] first, while the account they were made in is still the live one.
 */
class Accounts(
    private val store: AccountStore,
    private val signIn: GoogleSignIn,
    private val sessions: SessionActivation,
    private val ownerless: OwnerlessRows,
    private val watermarks: WatermarkReset,
    private val held: HeldWrites,
) {
    private val failure = MutableStateFlow<String?>(null)

    val accounts: StateFlow<List<Account>> get() = store.accounts
    val activeId: StateFlow<UserId?> get() = store.activeId

    /** Why the last attempt failed; null while none has. */
    val lastFailure: StateFlow<String?> = failure

    /** The held writes go before the sign-in, which on the web leaves the page. */
    suspend fun addAccount() {
        failure.value = null
        held.writeAll()
        reporting {
            val first = store.accounts.value.isEmpty()
            val session = signIn.signIn()
            val id = session.account.userId
            val wasManaged = store.accounts.value.any { it.userId == id && it.isManaged }
            store.add(session)
            if (wasManaged) watermarks.forget(id)
            sessions.activate(session)
            if (first) ownerless.claim(session.account.userId)
        }
    }

    // The store moves only once the session is live, so the avatar can never name an account the
    // app is not talking to the server as.
    suspend fun switchTo(id: UserId) {
        val session = store.actingSessionOf(id) ?: return
        held.writeAll()
        reporting {
            sessions.activate(session)
            store.switch(id)
        }
    }

    suspend fun rememberNickname(
        id: UserId,
        nickname: String?,
    ) = store.rememberNickname(id, nickname)

    suspend fun signOut(id: UserId) {
        held.writeAll()
        store.remove(id)
        val next = store.activeId.value?.let { store.actingSessionOf(it) }
        reporting { if (next == null) sessions.clear() else sessions.activate(next) }
    }

    /**
     * Google, the network and Supabase can each refuse, and every caller is a fire-and-forget
     * `launch`, where a throw lands on the main thread as an uncaught exception. A refusal is
     * recorded for the surface that offered the action instead.
     */
    private suspend fun reporting(work: suspend () -> Unit) {
        catching { work() }.onFailure { refused ->
            if (refused !is SignInCancelledException) failure.value = refused.toString()
        }
    }
}
