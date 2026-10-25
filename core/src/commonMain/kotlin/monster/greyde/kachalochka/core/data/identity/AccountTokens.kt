package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.data.SingleFlight
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Refreshing returns a session without making it the live one, which is what sync needs. */
interface SessionRefresh {
    /** Null when the server refuses the refresh token; a network failure throws. */
    suspend fun refresh(session: AccountSession): AccountSession?
}

/**
 * The access token of an owner's requests. A managed owner's is its guardian's; either comes
 * from the live session first, the store otherwise. Sync and the friends client ask for the same
 * owner at once on a cold start, so they share one refresh.
 */
class AccountTokens(
    private val store: AccountStore,
    private val live: LiveTokens,
    private val refresh: SessionRefresh,
    private val clock: Clock,
) {
    private val refreshes = SingleFlight<UserId, String?>()

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
    ): String? = refreshes.run(owner, { tokenFor(owner) }, renew)

    private fun usable(session: AccountSession): Boolean =
        clock.now() < session.expiresAt - 1.minutes
}
