package monster.greyde.kachalochka.core.data.identity

/** Google is the only way in; each platform runs its own flow and returns the session. */
interface GoogleSignIn {
    suspend fun signIn(): AccountSession
}
