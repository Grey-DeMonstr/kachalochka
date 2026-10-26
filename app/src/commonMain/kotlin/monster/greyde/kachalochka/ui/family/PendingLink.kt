package monster.greyde.kachalochka.ui.family

import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.ui.friends.JoinCodeStore

/** The Koin qualifier of the store that keeps a parent's code from a link. */
const val PARENT_CODE = "parentCode"

/** The Koin qualifier of the store that keeps a trainer's code from a link. */
const val TRAINER_CODE = "trainerCode"

/**
 * A parent's or a trainer's code from a link, linked by [accept] once the account agrees. A code
 * that could not reach the server stays stored for the next start.
 */
class PendingLink(
    private val store: JoinCodeStore,
    private val accept: suspend (String) -> Acceptance,
) {
    val waiting: Boolean get() = store.code() != null

    fun decline() = store.clear()

    suspend fun consume(): Acceptance? {
        val code = store.code() ?: return null
        val outcome = catching { accept(code) }.getOrElse { return null }
        store.clear()
        return outcome
    }
}
