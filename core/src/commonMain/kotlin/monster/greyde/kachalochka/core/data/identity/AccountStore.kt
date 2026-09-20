package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.domain.identity.UserId

interface AccountStore {
    val accounts: StateFlow<List<Account>>

    val activeId: StateFlow<UserId?>

    suspend fun add(session: AccountSession)

    suspend fun switch(id: UserId)

    suspend fun remove(id: UserId)

    suspend fun sessionOf(id: UserId): AccountSession?
}
