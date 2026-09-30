package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

data class Account(
    val userId: UserId,
    val email: String,
    val displayName: String,
    /** The Google picture's address, when Google sent one. */
    val pictureUrl: String? = null,
)

data class AccountSession(
    val account: Account,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
)
