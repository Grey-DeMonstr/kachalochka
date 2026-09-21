package monster.greyde.kachalochka.ui.account

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.identity.toAccountSession

private val GOOGLE_ID_TOKEN_TYPES =
    setOf(
        GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL,
    )

/** The client is resolved on the first sign-in, so a build without credentials still runs. */
class CredentialManagerGoogleSignIn(
    private val client: Lazy<SupabaseClient>,
    private val activity: () -> Activity?,
    private val webClientId: String,
) : GoogleSignIn {
    override suspend fun signIn(): AccountSession {
        check(webClientId.isNotBlank()) {
            "Google sign-in is not configured: set GOOGLE_WEB_CLIENT_ID in local.properties or " +
                "in the environment."
        }
        val screen = activity() ?: error("Sign-in needs a visible screen")
        val token = googleIdToken(screen)
        val auth = client.value.auth
        auth.signInWith(IDToken) {
            idToken = token
            provider = Google
        }
        val session = auth.currentSessionOrNull() ?: error("Google sign-in returned no session")
        return session.toAccountSession()
    }

    // The picker rather than the one-tap sheet: a second account is added precisely because the
    // one Android already knows about is not the one wanted.
    private suspend fun googleIdToken(screen: Activity): String {
        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
        val credential =
            CredentialManager
                .create(screen)
                .getCredential(screen, request)
                .credential
        check(credential is CustomCredential && credential.type in GOOGLE_ID_TOKEN_TYPES) {
            "Google sign-in returned a ${credential.type} instead of an ID token"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
