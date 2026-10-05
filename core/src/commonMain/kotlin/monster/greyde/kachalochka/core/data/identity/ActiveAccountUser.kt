package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId

/** One implementation for both targets: the owner of a new row is the active account. */
class ActiveAccountUser(
    private val store: AccountStore,
) : CurrentUser {
    override suspend fun id(): UserId? = store.activeId.value

    override suspend fun writesPrivateRows(): Boolean {
        val active = store.activeId.value
        return store.accounts.value.none { it.userId == active && it.isManaged }
    }
}
