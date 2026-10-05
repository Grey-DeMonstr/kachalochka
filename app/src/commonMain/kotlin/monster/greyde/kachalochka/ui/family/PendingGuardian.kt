package monster.greyde.kachalochka.ui.family

import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.ui.friends.JoinCodeStore
import monster.greyde.kachalochka.ui.friends.reading

/** The Koin qualifier of the store that keeps a parent's code from a link. */
const val PARENT_CODE = "parentCode"

/** A code that could not reach the server stays stored for the next start. */
class PendingGuardian(
    private val store: JoinCodeStore,
    private val family: FamilyRepository,
) {
    val waiting: Boolean get() = store.code() != null

    fun decline() = store.clear()

    suspend fun consume(): Acceptance? {
        val code = store.code() ?: return null
        val outcome = reading { family.accept(code) }.getOrElse { return null }
        store.clear()
        return outcome
    }
}
