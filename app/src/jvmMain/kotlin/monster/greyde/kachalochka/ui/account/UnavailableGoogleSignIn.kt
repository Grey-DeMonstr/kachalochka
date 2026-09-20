package monster.greyde.kachalochka.ui.account

import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn

/** The JVM target only hosts tests, and each one brings a sign-in of its own. */
object UnavailableGoogleSignIn : GoogleSignIn {
    override suspend fun signIn(): AccountSession =
        throw UnsupportedOperationException("Google sign-in has no desktop flow")
}
