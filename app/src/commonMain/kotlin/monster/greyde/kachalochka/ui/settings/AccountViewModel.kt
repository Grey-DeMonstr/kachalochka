package monster.greyde.kachalochka.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountDeletion
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.strings.AppStrings

/** Typed to confirm deleting the account, in any case. */
const val DELETE_WORD = "DELETE"

data class DeletionUi(
    val confirming: Boolean = false,
    val word: String = "",
    val running: Boolean = false,
    val error: String? = null,
) {
    val canConfirm: Boolean get() = word.trim().equals(DELETE_WORD, ignoreCase = true)
}

class AccountViewModel(
    private val accounts: Accounts,
    private val currentUser: CurrentUser,
    private val accountDeletion: AccountDeletion,
) : ViewModel() {
    /** The active account's e-mail; null once no account of its own is active. */
    val email: StateFlow<String?> =
        combine(accounts.accounts, accounts.activeId, ::emailOf)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                emailOf(accounts.accounts.value, accounts.activeId.value),
            )

    private val mutableDeletion = MutableStateFlow(DeletionUi())
    val deletion: StateFlow<DeletionUi> = mutableDeletion

    fun signOut() {
        viewModelScope.launch { accounts.activeId.value?.let { accounts.signOut(it) } }
    }

    fun askToDelete() {
        if (email.value == null || mutableDeletion.value.running) return
        mutableDeletion.value = DeletionUi(confirming = true)
    }

    fun typeDeleteWord(text: String) {
        mutableDeletion.value =
            mutableDeletion.value.copy(word = text.take(DELETE_WORD.length + 4))
    }

    fun cancelDelete() {
        mutableDeletion.value = mutableDeletion.value.copy(confirming = false, word = "")
    }

    /**
     * Signing the account out on success closes the screen. Leaving the screen must not stop it
     * halfway, between the server forgetting the account and the device.
     */
    fun confirmDelete() {
        if (!mutableDeletion.value.canConfirm) return
        mutableDeletion.value = mutableDeletion.value.copy(confirming = false, word = "")
        viewModelScope.launch {
            val owner = currentUser.id() ?: return@launch
            mutableDeletion.value = mutableDeletion.value.copy(running = true)
            catching { withContext(NonCancellable) { accountDeletion.delete(owner) } }.onFailure {
                mutableDeletion.value = DeletionUi(error = AppStrings.current.offline)
            }
        }
    }
}

private fun emailOf(
    accounts: List<Account>,
    active: UserId?,
): String? = accounts.firstOrNull { it.userId == active }?.takeUnless { it.isManaged }?.email
