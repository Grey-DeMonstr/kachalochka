package monster.greyde.kachalochka.core.data.family

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.concurrent.Volatile

/** Reads [owner]'s family as [owner], whoever is active meanwhile. */
fun interface FamilyReads {
    suspend fun familyOf(owner: UserId): Family
}

/** The account a family read is made as; the client asks it for its token. */
class ActingAccount {
    @Volatile var owner: UserId? = null
}

/** One client for every account: the reads take turns, each naming its account to it. */
class AccountFamilyReads(
    repository: (ActingAccount) -> FamilyRepository,
) : FamilyReads {
    private val acting = ActingAccount()
    private val family = repository(acting)
    private val lock = Mutex()

    override suspend fun familyOf(owner: UserId): Family =
        lock.withLock {
            acting.owner = owner
            try {
                family.family()
            } finally {
                acting.owner = null
            }
        }
}
