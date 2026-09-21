package monster.greyde.kachalochka.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.monogram

data class AccountsUi(
    val accounts: List<AccountUi>,
    val activeId: UserId?,
)

data class AccountUi(
    val id: UserId,
    val displayName: String,
    val email: String,
    val monogram: String,
    val active: Boolean,
)

fun accountsUi(
    accounts: List<Account>,
    activeId: UserId?,
): List<AccountUi> =
    accounts.map {
        AccountUi(
            it.userId,
            it.displayName,
            it.email,
            monogram(it.displayName),
            it.userId == activeId,
        )
    }

/**
 * Resolved at more than one `ViewModelStoreOwner` (the app-level sign-in gate and each screen),
 * so distinct instances coexist. They only agree because [state] derives entirely from [accounts];
 * any local mutable state added here would let those instances drift apart.
 */
class AccountsViewModel(
    private val accounts: Accounts,
) : ViewModel() {
    val state: StateFlow<AccountsUi> =
        combine(accounts.accounts, accounts.activeId) { list, activeId ->
            AccountsUi(accountsUi(list, activeId), activeId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountsUi(emptyList(), null))

    fun addAccount() {
        viewModelScope.launch {
            try {
                accounts.addAccount()
            } catch (error: Exception) {
                if (!isUserCancellation(error)) throw error
            }
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

/**
 * Android's picker throws `GetCredentialCancellationException` when the user backs out; that
 * type lives in androidx.credentials, invisible from commonMain, so its class name is the only
 * cross-platform way to tell a change of mind from a broken sign-in.
 */
internal fun isUserCancellation(error: Throwable): Boolean =
    error::class.simpleName == "GetCredentialCancellationException"
