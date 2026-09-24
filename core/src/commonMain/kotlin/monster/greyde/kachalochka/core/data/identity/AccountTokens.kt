package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Refreshing returns a session without making it the live one, which is what sync needs. */
interface SessionRefresh {
    /** Null when the server refuses the refresh token; a network failure throws. */
    suspend fun refresh(session: AccountSession): AccountSession?
}

/** Sync's source of access tokens: the live session first, the store otherwise. */
class AccountTokens(
    private val store: AccountStore,
    private val live: LiveTokens,
    private val refresh: SessionRefresh,
    private val clock: Clock,
) {
    suspend fun tokenFor(owner: UserId): String? {
        live.liveSessionOf(owner)?.let {
            return if (usable(it)) it.accessToken else live.refreshLive(owner)
        }
        val session = store.sessionOf(owner) ?: return null
        if (usable(session)) return session.accessToken
        val renewed = refresh.refresh(session) ?: return null
        store.replaceSession(renewed)
        return renewed.accessToken
    }

    private fun usable(session: AccountSession): Boolean =
        clock.now() < session.expiresAt - 1.minutes
}
