package monster.greyde.kachalochka.ui.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import kotlinx.browser.window
import kotlinx.coroutines.awaitCancellation
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.identity.toAccountSession

/**
 * Google takes the whole page, so there is nobody left to hand a session to: the browser returns
 * to a fresh start-up, where [sessionFromRedirect] picks the answer up.
 */
class RedirectGoogleSignIn(
    private val client: SupabaseClient,
) : GoogleSignIn {
    override suspend fun signIn(): AccountSession {
        client.auth.signInWith(Google, redirectUrl = signInReturnAddress(window.location.href))
        awaitCancellation()
    }
}

/**
 * supabase-kt reads Google's answer out of the URL while the Auth plugin starts, and strips it
 * with `history.replaceState` so a reload cannot replay it.
 *
 * Nothing else imports a session this early, so whatever is live once the plugin is up came back
 * from the redirect.
 */
suspend fun SupabaseClient.sessionFromRedirect(): AccountSession? {
    auth.awaitInitialization()
    return auth.currentSessionOrNull()?.toAccountSession()
}
