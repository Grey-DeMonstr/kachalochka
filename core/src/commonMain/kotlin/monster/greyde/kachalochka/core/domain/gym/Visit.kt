package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

data class Visit(
    val id: VisitId,
    val userId: UserId?,
    val recordedAt: Instant,
    val endedAt: Instant?,
    val updatedAt: Instant,
    val deleted: Boolean,
)
