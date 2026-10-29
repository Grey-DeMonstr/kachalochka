package monster.greyde.kachalochka.core.data.sync

import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.gym.PriorBestsKeeper
import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * A pass, then normalization of what it pulled and a recompute of the prior bests. What they
 * wrote goes out in a second pass of the same job: requesting one would replace, and so cancel,
 * the job asking for it.
 *
 * An owner whose normalization throws goes to [failed] and leaves the pass clean: every pass
 * normalizes again, and a retry would only repeat the failure sooner. A trainee's visits are left
 * as they are: their trainer may not write them.
 */
suspend fun SyncPass.runAndNormalize(
    owners: List<UserId>,
    normalizer: VisitNormalizer,
    keeper: PriorBestsKeeper,
    failed: (Throwable) -> Unit,
    managed: Set<UserId> = emptySet(),
    trainees: Set<UserId> = emptySet(),
): Boolean {
    val clean = owners.filter { run(listOf(it), managed, trainees) }
    // A copy that has not pulled may be older than the server's rows its writes would replace.
    val wrote =
        clean
            .filter { it !in trainees }
            .map { owner ->
                val normalized =
                    catching { normalizer.normalize(owner) }.getOrElse { failure ->
                        failed(failure)
                        false
                    }
                val refreshed = keeper.refreshAll(owner)
                normalized || refreshed
            }.any { it }
    return if (wrote) run(owners, managed, trainees) else clean.size == owners.size
}
