package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.UserId

/** Row-level security rejects a write with no owner, so the web target has none to claim. */
object NoOwnerlessRows : OwnerlessRows {
    override suspend fun claim(owner: UserId) = Unit
}
