package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.UserId

/** The web keeps no rows on the device. */
object NoOwnedRowsPurge : OwnedRowsPurge {
    override suspend fun purge(owner: UserId) = Unit
}
