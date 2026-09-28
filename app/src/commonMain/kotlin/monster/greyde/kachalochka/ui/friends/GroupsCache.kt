package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import org.koin.core.Koin

/** The active account's groups, read ahead so the friends screen opens with them. */
class GroupsCache(
    private val friends: FriendsRepository,
    private val accounts: Accounts,
    private val scope: CoroutineScope,
) {
    // Keyed by owner, so a read that outlives an account switch lands under its own account.
    private val byOwner = MutableStateFlow(emptyMap<UserId, List<FriendGroup>>())

    /** Groups last read for [owner], or null when none were read for that account. */
    fun cached(owner: UserId): List<FriendGroup>? = byOwner.value[owner]

    /** Reads the groups again and remembers them; throws what the read throws. */
    suspend fun refresh(owner: UserId): List<FriendGroup> {
        val found = friends.groups()
        byOwner.update { it + (owner to found) }
        return found
    }

    /** Reads ahead for every account that becomes active, ignoring failures. */
    fun warm() {
        scope.launch {
            accounts.activeId.filterNotNull().collectLatest { owner -> reading { refresh(owner) } }
        }
    }
}

/** A build without credentials has no groups to read ahead, and must not build a client. */
fun Koin.warmGroups() {
    if (get<SupabaseCredentials>().isConfigured) get<GroupsCache>().warm()
}
