package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

/** One account's calendar day; [day] is null only on rows normalization has not reached. */
data class Visit(
    val id: VisitId,
    val userId: UserId?,
    val day: CalendarDay?,
    val recordedAt: Instant,
    val updatedAt: Instant,
    val deleted: Boolean,
)

/** Of several live visits on one day, the greatest is the one normalization keeps. */
val visitRecency: Comparator<Visit> =
    compareBy<Visit>({ it.recordedAt }, { it.updatedAt }, { it.id.value })
