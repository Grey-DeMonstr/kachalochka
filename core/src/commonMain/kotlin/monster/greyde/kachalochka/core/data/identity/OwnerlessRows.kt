package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.UserId

/** Android records before anyone signs in; the first account to sign in takes those rows. */
interface OwnerlessRows {
    suspend fun claim(owner: UserId)
}
