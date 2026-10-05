package monster.greyde.kachalochka.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.friends.Invite
import monster.greyde.kachalochka.ui.friends.InviteSharing
import monster.greyde.kachalochka.ui.friends.guardianLink
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.strings.AppStrings

/**
 * [children] is null until the first read; [code] is the one offered while the screen is open,
 * with its [link] when the page address is known.
 */
data class ChildrenUiState(
    val children: List<FamilyMember>? = null,
    val offline: Boolean = false,
    val code: String? = null,
    val link: String? = null,
    val notice: String? = null,
    val removing: FamilyMember? = null,
)

class ChildrenViewModel(
    private val family: FamilyRepository,
    private val invites: InviteSharing,
    private val accounts: Accounts,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChildrenUiState())
    val state: StateFlow<ChildrenUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)
    private var loading: Job? = null

    /** A code offered for one account is never shown for another. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                mutableState.value = ChildrenUiState()
                load()
            }
        }
    }

    fun load() {
        loading?.cancel()
        loading = viewModelScope.launch { read() }
    }

    fun addChild() =
        writes.launch {
            reading { family.offer() }
                .onSuccess { code ->
                    val link = invites.pageAddress?.let { guardianLink(it, code) }
                    mutableState.update { it.copy(code = code, link = link, notice = null) }
                }.onFailure { offline() }
        }

    fun share() {
        val code = state.value.code ?: return
        writes.launch {
            val link = invites.pageAddress?.let { guardianLink(it, code) }
            val invite = Invite(AppStrings.current.guardianInviteMessage, code, link)
            val notice = reading { invites.share(invite) }.getOrNull()
            mutableState.update { it.copy(notice = notice) }
        }
    }

    fun askToRemove(child: FamilyMember) = mutableState.update { it.copy(removing = child) }

    fun cancelRemove() = mutableState.update { it.copy(removing = null) }

    fun confirmRemove() {
        val child = state.value.removing ?: return
        val me = accounts.activeId.value ?: return
        mutableState.update { it.copy(removing = null) }
        writes.launch {
            reading { family.end(child.userId, me) }
                .onSuccess { read() }
                .onFailure { offline() }
        }
    }

    private suspend fun read() {
        reading { family.family().children }
            .onSuccess { found ->
                mutableState.update { it.copy(children = found, offline = false) }
            }.onFailure { mutableState.update { it.copy(offline = true) } }
    }

    private fun offline() = mutableState.update { it.copy(notice = AppStrings.current.offline) }
}
