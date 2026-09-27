package monster.greyde.kachalochka.core.data.sync

import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * A pass, then normalization of what it pulled. What normalization wrote goes out in a second
 * pass of the same job: requesting one would replace, and so cancel, the job asking for it.
 */
suspend fun SyncPass.runAndNormalize(
    owners: List<UserId>,
    normalizer: VisitNormalizer,
): Boolean {
    val clean = owners.filter { run(listOf(it)) }
    // A copy that has not pulled may be older than the server's rows its writes would replace.
    val wrote = clean.map { normalizer.normalize(it) }.any { it }
    return if (wrote) run(owners) else clean.size == owners.size
}
