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
    /** Machines a started plan added, in plan order, whether or not they have sets yet. */
    val planned: List<MachineId> = emptyList(),
)

/**
 * Of several live visits on one day, the greatest is the one shown; normalization keeps the
 * greatest of those with sets.
 */
val visitRecency: Comparator<Visit> =
    compareBy<Visit>({ it.recordedAt }, { it.updatedAt }, { it.id.value })
