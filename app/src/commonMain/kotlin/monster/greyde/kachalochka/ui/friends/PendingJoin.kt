package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupId

sealed interface JoinOutcome {
    data class Joined(
        val group: GroupId,
    ) : JoinOutcome

    data object NotFound : JoinOutcome
}

/** A code that could not reach the server stays stored for the next start. */
class PendingJoin(
    private val store: JoinCodeStore,
    private val friends: FriendsRepository,
) {
    val waiting: Boolean get() = store.code() != null

    fun decline() = store.clear()

    suspend fun consume(): JoinOutcome? {
        val code = store.code() ?: return null
        val joined = reading { friends.join(code) }.getOrElse { return null }
        store.clear()
        return if (joined == null) JoinOutcome.NotFound else JoinOutcome.Joined(joined)
    }
}
