package monster.greyde.kachalochka.core.domain.profile

import kotlin.time.Instant

data class Profile(
    val id: String,
    val userId: String?,
    val displayName: String?,
    val updatedAt: Instant,
    val deleted: Boolean,
)
