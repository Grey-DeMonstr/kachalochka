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

/** The token of the account live on the UI client, which that client keeps refreshed itself. */
fun interface LiveTokens {
    fun accessTokenOf(owner: UserId): String?
}

/**
 * The library ends a session it could not refresh exactly as the app ends one on purpose, so an
 * end signs its account out only when it follows that account going live and the app still
 * intends it to be.
 */
class LiveSession(
    private val sessions: SessionActivation,
    private val store: AccountStore,
) : SessionActivation,
    LiveTokens {
    // Written on the main thread, read by the sync worker.
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

    override fun accessTokenOf(owner: UserId): String? =
        seen?.takeIf { it.account.userId == owner && owner == intended }?.accessToken

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
