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
    val day: CalendarDay? = null,
)

/** Of several live visits on one day, the greatest is the one normalization keeps. */
val visitRecency: Comparator<Visit> =
    compareBy<Visit>({ it.recordedAt }, { it.updatedAt }, { it.id.value })
