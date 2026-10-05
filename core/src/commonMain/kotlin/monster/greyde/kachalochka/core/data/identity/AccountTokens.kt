package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Refreshing returns a session without making it the live one, which is what sync needs. */
interface SessionRefresh {
    /** Null when the server refuses the refresh token; a network failure throws. */
    suspend fun refresh(session: AccountSession): AccountSession?
}

/**
 * Sync's source of access tokens: the live session first, the store otherwise. Sync and the
 * friends client ask for the same owner at once on a cold start, so they share one refresh.
 */
class AccountTokens(
    private val store: AccountStore,
    private val live: LiveTokens,
    private val refresh: SessionRefresh,
    private val clock: Clock,
) {
    private val lock = Mutex()
    private val inFlight = mutableMapOf<UserId, CompletableDeferred<String?>>()

    suspend fun tokenFor(owner: UserId): String? {
        guardianOf(owner)?.let { return tokenFor(it) }
        live.liveSessionOf(owner)?.let {
            return if (usable(it)) it.accessToken else once(owner) { live.refreshLive(owner) }
        }
        val session = store.sessionOf(owner) ?: return null
        if (usable(session)) return session.accessToken
        return once(owner) { renewStored(owner) }
    }

    // A managed account has no session; its guardian's acts for it.
    private fun guardianOf(owner: UserId): UserId? =
        store.accounts.value
            .firstOrNull { it.userId == owner }
            ?.guardianId

    // Read again: a refresh that finished since the caller looked has already rotated the token.
    private suspend fun renewStored(owner: UserId): String? {
        val session = store.sessionOf(owner) ?: return null
        if (usable(session)) return session.accessToken
        val renewed = refresh.refresh(session) ?: return null
        store.replaceSession(renewed)
        return renewed.accessToken
    }

    private suspend fun once(
        owner: UserId,
        renew: suspend () -> String?,
    ): String? {
        var leads = false
        val shared =
            lock.withLock {
                inFlight.getOrPut(owner) { CompletableDeferred<String?>().also { leads = true } }
            }
        if (!leads) {
            return try {
                shared.await()
            } catch (stopped: CancellationException) {
                // The refresh was cancelled with the caller that ran it, not with this one.
                currentCoroutineContext().ensureActive()
                tokenFor(owner)
            }
        }
        val result = runCatching { renew() }
        withContext(NonCancellable) { lock.withLock { inFlight.remove(owner) } }
        shared.completeWith(result)
        return result.getOrThrow()
    }

    private fun usable(session: AccountSession): Boolean =
        clock.now() < session.expiresAt - 1.minutes
}
