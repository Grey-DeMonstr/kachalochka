package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.dayAt
import monster.greyde.kachalochka.core.domain.gym.normalizedVisits
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Applies [normalizedVisits] to one owner through the repositories (tech spec §4.5). */
class VisitNormalizer(
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
    private val clock: Clock,
    private val utcOffset: (Instant) -> Duration,
) {
    /** True when it wrote anything. */
    suspend fun normalize(owner: UserId?): Boolean {
        val owned = visits.all(owner)
        // On the web each read is a request, and only a visit that shares its day can be removed.
        val crowded =
            owned
                .groupBy { it.dayAt(utcOffset) }
                .values
                .filter { it.size > 1 }
                .flatten()
        val theirSets = crowded.flatMap { sets.forVisit(it.id) }
        val rows = normalizedVisits(owned, theirSets, utcOffset, clock.now())
        rows.forEach { row ->
            row.sets.forEach { sets.upsert(it) }
            visits.upsert(row.visit)
        }
        return rows.isNotEmpty()
    }
}
