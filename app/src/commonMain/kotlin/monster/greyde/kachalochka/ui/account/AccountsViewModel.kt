package monster.greyde.kachalochka.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountKind
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.monogram
import monster.greyde.kachalochka.ui.strings.AppStrings

data class AccountsUi(
    val accounts: List<AccountUi>,
    val activeId: UserId?,
    val failure: String? = null,
) {
    /** A managed child is active, so its private sections stay hidden. */
    val managedActive: Boolean get() = accounts.any { it.active && it.managed }
}

data class AccountUi(
    val id: UserId,
    val displayName: String,
    val email: String,
    val monogram: String,
    val active: Boolean,
    val avatar: Avatar = Avatar(),
    val managed: Boolean = false,
)

fun accountsUi(
    accounts: List<Account>,
    activeId: UserId?,
    chosen: Map<UserId, PhotoId> = emptyMap(),
): List<AccountUi> =
    accounts.map {
        AccountUi(
            it.userId,
            it.displayName,
            it.email,
            monogram(it.displayName),
            it.userId == activeId,
            accountAvatar(it, chosen),
            managed = it.isManaged,
        )
    }

// A child's profile is not on the parent's device, so its chosen photo comes with its entry.
fun accountAvatar(
    account: Account,
    chosen: Map<UserId, PhotoId>,
): Avatar {
    val managed = account.kind as? AccountKind.Managed
    return Avatar(chosen[account.userId] ?: managed?.avatarPhoto, account.pictureUrl)
}

/**
 * Resolved at more than one `ViewModelStoreOwner` (the app-level sign-in gate and each screen),
 * so distinct instances coexist. They only agree because [state] derives entirely from [accounts]
 * and the process-wide [avatars]; any local mutable state added here would let them drift apart.
 */
class AccountsViewModel(
    private val accounts: Accounts,
    private val sync: SyncTrigger,
    private val avatars: AccountAvatars,
) : ViewModel() {
    val state: StateFlow<AccountsUi> =
        combine(
            accounts.accounts,
            accounts.activeId,
            accounts.lastFailure,
            avatars.photos,
        ) { list, activeId, failure, chosen ->
            AccountsUi(
                accountsUi(list, activeId, chosen),
                activeId,
                // The design draws no error screen, and a failed sign-in still has to say so.
                failure?.let { AppStrings.current.signInFailed },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountsUi(emptyList(), null))

    init {
        viewModelScope.launch { accounts.accounts.collect { avatars.refresh() } }
        viewModelScope.launch { sync.completed.collect { avatars.refresh() } }
    }

    fun addAccount() {
        viewModelScope.launch {
            accounts.addAccount()
            sync.request()
        }
    }

    fun switchTo(id: UserId) {
        viewModelScope.launch { accounts.switchTo(id) }
    }

    fun signOutActive() {
        viewModelScope.launch {
            state.value.activeId?.let { accounts.signOut(it) }
        }
    }
}
