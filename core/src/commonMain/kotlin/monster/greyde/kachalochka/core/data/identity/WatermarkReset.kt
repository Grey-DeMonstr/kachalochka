package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * A managed account pulls only its gym tables; once it signs in itself, its next pull must start
 * from scratch to fetch the rest.
 */
fun interface WatermarkReset {
    suspend fun forget(owner: UserId)
}

/** The web keeps no pull watermarks. */
object NoWatermarkReset : WatermarkReset {
    override suspend fun forget(owner: UserId) = Unit
}
