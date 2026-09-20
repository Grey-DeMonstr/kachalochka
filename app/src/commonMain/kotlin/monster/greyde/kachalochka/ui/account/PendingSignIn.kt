package monster.greyde.kachalochka.ui.account

import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation

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
 * A page load restores the account list from storage but leaves Supabase holding nothing, and
 * row-level security turns away every request until the stored session is live again.
 *
 * An expired one goes through too: supabase-kt refreshes what it is given, and dropping the
 * account instead would sign the user out an hour after every visit.
 */
suspend fun resumeActiveAccount(
    store: AccountStore,
    sessions: SessionActivation,
): Boolean {
    val session = store.activeId.value?.let { store.sessionOf(it) } ?: return false
    sessions.activate(session)
    return true
}
