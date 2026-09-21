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
    val failure: String? = null,
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
        combine(
            accounts.accounts,
            accounts.activeId,
            accounts.lastFailure,
        ) { list, activeId, failure ->
            AccountsUi(accountsUi(list, activeId), activeId, failure?.let { SIGN_IN_FAILED })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountsUi(emptyList(), null))

    fun addAccount() {
        viewModelScope.launch { accounts.addAccount() }
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

/** The design draws no error screen, and a sign-in that failed still has to say that it did. */
private const val SIGN_IN_FAILED = "Не удалось войти. Попробуйте ещё раз"
