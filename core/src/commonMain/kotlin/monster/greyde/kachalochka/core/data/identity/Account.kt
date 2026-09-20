package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

data class Account(
    val userId: UserId,
    val email: String,
    val displayName: String,
)

data class AccountSession(
    val account: Account,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
)
