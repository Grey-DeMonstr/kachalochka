package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.concurrent.Volatile

/** What supabase-kt did to the live session on its own. */
sealed interface LiveSessionChange {
    data class Renewed(
        val session: AccountSession,
    ) : LiveSessionChange

    data object Ended : LiveSessionChange

    /** The library is reloading its session; an end it reports next is not a refused refresh. */
    data object Reloading : LiveSessionChange
}

/**
 * The session of the account live on the UI client. Only that client may refresh it: its
 * refresh token rotates, and a refresh from anywhere else would leave the client holding a spent
 * one.
 */
interface LiveTokens {
    /** Null unless [owner] is the account live on the UI client. */
    fun liveSessionOf(owner: UserId): AccountSession?

    /** Null when [owner] is no longer live or the server refuses the refresh. */
    suspend fun refreshLive(owner: UserId): String?
}

/** The UI client refreshing the session live on it. */
fun interface LiveRefresh {
    /** Null when the server refuses the refresh token; a network failure throws. */
    suspend fun refreshLive(): AccountSession?
}

/**
 * The library ends a session it could not refresh exactly as the app ends one on purpose, so an
 * end signs its account out only when it follows that account going live and the app still
 * intends it to be.
 */
class LiveSession(
    private val sessions: SessionActivation,
    private val refresher: LiveRefresh,
    private val store: AccountStore,
) : SessionActivation,
    LiveTokens {
    // Shared between the main thread and the sync worker.
    @Volatile private var intended: UserId? = null

    @Volatile private var seen: AccountSession? = null

    // A refused switch leaves the previous account live, so the guard stays on it.
    override suspend fun activate(session: AccountSession) {
        sessions.activate(session)
        intended = session.account.userId
    }

    override suspend fun clear() {
        intended = null
        sessions.clear()
    }

    override fun liveSessionOf(owner: UserId): AccountSession? =
        seen?.takeIf { it.account.userId == owner && owner == intended }

    override suspend fun refreshLive(owner: UserId): String? {
        if (liveSessionOf(owner) == null) return null
        val renewed = refresher.refreshLive()?.takeIf { it.account.userId == owner } ?: return null
        // The follower writes it back as well, but the pass asks for a token before it gets to.
        seen = renewed
        return renewed.accessToken
    }

    suspend fun follow(changes: Flow<LiveSessionChange>) {
        changes.collect { change ->
            try {
                absorb(change)
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (refused: Exception) {
                // A follower that died on one refusal would stop writing tokens back for the rest
                // of the process.
            }
        }
    }

    private suspend fun absorb(change: LiveSessionChange) {
        when (change) {
            is LiveSessionChange.Renewed -> {
                seen = change.session
                store.replaceSession(change.session)
            }
            LiveSessionChange.Ended -> {
                val gone = seen?.account?.userId
                seen = null
                if (gone == null || gone != intended) return
                store.remove(gone)
                val next = store.activeId.value?.let { store.sessionOf(it) }
                if (next == null) clear() else activateOrDisown(next)
            }
            LiveSessionChange.Reloading -> seen = null
        }
    }

    // The account stays listed, so a tap on it retries; until then nobody reads as signed in.
    private suspend fun activateOrDisown(next: AccountSession) {
        try {
            activate(next)
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (refused: Exception) {
            store.deactivate()
            clear()
        }
    }
}
