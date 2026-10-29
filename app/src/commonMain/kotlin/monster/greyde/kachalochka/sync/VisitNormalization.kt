package monster.greyde.kachalochka.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.gym.PriorBestsKeeper
import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.identity.UserId
import org.koin.core.Koin

/**
 * Where the visits normalization rewrites live. A device copy of an account's visits is
 * normalized only by the sync pass, after it pulls: normalized before that, it would push old
 * rows over newer ones.
 */
enum class VisitStore { Server, Device }

/**
 * Normalizes the owners given at start, then every account that becomes active, or on a device
 * asks for the pass that does; a run that wrote asks for a pass. A run that fails, as the web does
 * offline, is recorded and waits for the next start or switch.
 *
 * The prior bests follow a normalization that wrote, since it removes sets. The anonymous owner
 * never pulls, so theirs are refreshed at every start; the refresh is idempotent and their data
 * is small.
 */
class VisitNormalization(
    private val normalizer: VisitNormalizer,
    private val priorBests: PriorBestsKeeper,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
    private val store: VisitStore,
    private val failures: FailureLog,
) {
    suspend fun run(atStart: List<UserId?>) {
        normalize(atStart)
        accounts.activeId.filterNotNull().collect { active ->
            when (store) {
                VisitStore.Server -> normalize(listOf(active))
                VisitStore.Device -> sync.request()
            }
        }
    }

    private suspend fun normalize(owners: List<UserId?>) {
        val wrote = owners.map { attempt(it) }.any { it }
        if (wrote) sync.request()
    }

    // Its trainer may not write a trainee's visits, so they stay as the trainee left them.
    private fun coached(owner: UserId?): Boolean =
        accounts.accounts.value.any { it.userId == owner && it.isTrainee }

    private suspend fun attempt(owner: UserId?): Boolean =
        catching {
            if (coached(owner)) return@catching false
            val normalized = normalizer.normalize(owner)
            val refreshed = (normalized || owner == null) && priorBests.refreshAll(owner)
            normalized || refreshed
        }.getOrElse { failed ->
            failures.record(failed)
            false
        }
}

/** Runs [VisitNormalization] for the life of the process. */
fun Koin.startVisitNormalization(atStart: List<UserId?>) {
    val normalization = get<VisitNormalization>()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { normalization.run(atStart) }
}
