package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.ui.friends.reading
import org.koin.core.Koin

/**
 * Keeps the links of every account that becomes active direct, so links a friend made since the
 * last start reach this account's machines without a screen that reads the group. A read that
 * fails, as offline, waits for the next start or switch.
 */
class LinkUpkeep(
    private val catalogue: MachineCatalogue,
    private val accounts: Accounts,
) {
    suspend fun run() =
        accounts.activeId.filterNotNull().collectLatest { owner ->
            reading { catalogue.group(owner) }
        }
}

/** A build without credentials has no group to read. */
fun Koin.startLinkUpkeep() {
    if (!get<SupabaseCredentials>().isConfigured) return
    val upkeep = get<LinkUpkeep>()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { upkeep.run() }
}
