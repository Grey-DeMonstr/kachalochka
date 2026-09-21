package monster.greyde.kachalochka.ui

import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * A route names the visit the screen was opened from, which the account active now may not own.
 * Its own active visit stands in for it then, so nothing is read or written across the boundary.
 */
internal suspend fun VisitRepository.ownVisit(
    routeId: VisitId,
    owner: UserId?,
): Visit? = byId(routeId)?.takeIf { it.userId == owner } ?: active(owner)
