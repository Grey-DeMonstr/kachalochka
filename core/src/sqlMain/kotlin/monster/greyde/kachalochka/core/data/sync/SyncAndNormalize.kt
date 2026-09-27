package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.CancellationException
import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * A pass, then normalization of what it pulled. What normalization wrote goes out in a second
 * pass of the same job: requesting one would replace, and so cancel, the job asking for it.
 *
 * An owner whose normalization throws goes to [failed] and leaves the pass clean: every pass
 * normalizes again, and a retry would only repeat the failure sooner.
 */
suspend fun SyncPass.runAndNormalize(
    owners: List<UserId>,
    normalizer: VisitNormalizer,
    failed: (Throwable) -> Unit,
): Boolean {
    val clean = owners.filter { run(listOf(it)) }
    // A copy that has not pulled may be older than the server's rows its writes would replace.
    val wrote =
        clean
            .map { owner ->
                try {
                    normalizer.normalize(owner)
                } catch (stopped: CancellationException) {
                    throw stopped
                } catch (failure: Exception) {
                    failed(failure)
                    false
                }
            }.any { it }
    return if (wrote) run(owners) else clean.size == owners.size
}
