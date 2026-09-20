package monster.greyde.kachalochka.ui.account

import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation

/**
 * The store survives a page load and the live session does not, so start-up either puts one back
 * or stops the app claiming its owner is signed in: an account without a session reads as signed
 * in while row-level security rejects every query made under it.
 *
 * @param returned the session the browser came back from Google with, if it came back from one.
 */
suspend fun restoreSession(
    store: AccountStore,
    sessions: SessionActivation,
    report: (String) -> Unit,
    returned: suspend () -> AccountSession?,
): Boolean {
    val live =
        try {
            completeSignIn(returned(), store, sessions) || resumeActiveAccount(store, sessions)
        } catch (failure: Exception) {
            report("Could not restore the signed-in account: $failure")
            false
        }
    if (!live) disownActiveAccount(store, sessions)
    return live
}

/**
 * Google takes the whole page on the web, so the browser comes back to a start-up that knows
 * nothing of the visit that left: the session it carries is the only trace of it.
 */
suspend fun completeSignIn(
    returned: AccountSession?,
    store: AccountStore,
    sessions: SessionActivation,
): Boolean {
    if (returned == null) return false
    store.add(returned)
    sessions.activate(returned)
    return true
}

/**
 * A page load restores the account list from storage but leaves Supabase holding nothing.
 *
 * An expired session goes through too: supabase-kt refreshes what it is given.
 */
suspend fun resumeActiveAccount(
    store: AccountStore,
    sessions: SessionActivation,
): Boolean {
    val session = store.activeId.value?.let { store.sessionOf(it) } ?: return false
    sessions.activate(session)
    return true
}

/** The accounts stay listed, so the one that could not be restored is a tap away again. */
suspend fun disownActiveAccount(
    store: AccountStore,
    sessions: SessionActivation,
) {
    store.deactivate()
    sessions.clear()
}
