package monster.greyde.kachalochka.core.data.identity

/** Google is the only way in; each platform runs its own flow and returns the session. */
interface GoogleSignIn {
    /** Throws [SignInCancelledException] when the user backs out of the flow. */
    suspend fun signIn(): AccountSession
}

/** A change of mind, which the app does not report as a failed sign-in. */
class SignInCancelledException(
    cause: Throwable? = null,
) : Exception("The user backed out of sign-in", cause)
