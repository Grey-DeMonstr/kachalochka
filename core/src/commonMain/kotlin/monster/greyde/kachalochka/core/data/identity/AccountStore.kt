package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.domain.identity.UserId

interface AccountStore {
    val accounts: StateFlow<List<Account>>

    val activeId: StateFlow<UserId?>

    suspend fun add(session: AccountSession)

    suspend fun switch(id: UserId)

    /** Signing a guardian out takes the accounts it acts for along. */
    suspend fun remove(id: UserId)

    /** Swaps in fresh tokens for an account already stored; anybody else is ignored. */
    suspend fun replaceSession(session: AccountSession)

    /** Leaves the accounts in place and nobody signed in. */
    suspend fun deactivate()

    suspend fun sessionOf(id: UserId): AccountSession?

    /** The session acting for [id]: its own, or its guardian's when [id] is managed. */
    suspend fun actingSessionOf(id: UserId): AccountSession?

    /**
     * Replaces the managed accounts. One whose guardian holds no session here, or that is signed
     * in with Google here, is left out; when the active one goes, its guardian becomes active.
     */
    suspend fun setManaged(children: List<Account>)
}
