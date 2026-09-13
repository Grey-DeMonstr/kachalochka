package monster.greyde.kachalochka.core.domain.profile

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

data class Profile(
    val id: ProfileId,
    val userId: UserId?,
    val displayName: String?,
    val updatedAt: Instant,
    val deleted: Boolean,
)
