package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.domain.identity.UserId

class Accounts(
    private val store: AccountStore,
    private val signIn: GoogleSignIn,
    private val sessions: SessionActivation,
    private val ownerless: OwnerlessRows,
) {
    val accounts: StateFlow<List<Account>> get() = store.accounts
    val activeId: StateFlow<UserId?> get() = store.activeId

    suspend fun addAccount() {
        val first = store.accounts.value.isEmpty()
        val session = signIn.signIn()
        store.add(session)
        sessions.activate(session)
        if (first) ownerless.claim(session.account.userId)
    }

    suspend fun switchTo(id: UserId) {
        val session = store.sessionOf(id) ?: return
        store.switch(id)
        sessions.activate(session)
    }

    suspend fun signOut(id: UserId) {
        store.remove(id)
        val next = store.activeId.value?.let { store.sessionOf(it) }
        if (next == null) sessions.clear() else sessions.activate(next)
    }
}
