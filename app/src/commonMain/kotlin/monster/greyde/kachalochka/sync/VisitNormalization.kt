package monster.greyde.kachalochka.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.identity.UserId
import org.koin.core.Koin

/**
 * Normalizes the owners given at start, then every account that becomes active; a run that wrote
 * asks for a pass. A run that fails, as the web does offline, waits for the next start or switch.
 */
class VisitNormalization(
    private val normalizer: VisitNormalizer,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
) {
    suspend fun run(atStart: List<UserId?>) {
        normalize(atStart)
        accounts.activeId.filterNotNull().collect { normalize(listOf(it)) }
    }

    private suspend fun normalize(owners: List<UserId?>) {
        val wrote = owners.map { attempt(it) }.any { it }
        if (wrote) sync.request()
    }

    private suspend fun attempt(owner: UserId?): Boolean =
        try {
            normalizer.normalize(owner)
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failed: Exception) {
            false
        }
}

/** Runs [VisitNormalization] for the life of the process. */
fun Koin.startVisitNormalization(atStart: List<UserId?>) {
    val normalization = get<VisitNormalization>()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { normalization.run(atStart) }
}
