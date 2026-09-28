package monster.greyde.kachalochka.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.friends.GroupMember
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.WriteGuard

data class GroupUiState(
    val title: String,
    val code: String,
    val members: List<MemberRowUi>,
    val isOwner: Boolean,
    val confirming: GroupConfirmUi?,
    val notice: String?,
)

data class MemberRowUi(
    val friend: Friend,
    val owner: Boolean,
    val opens: Boolean,
)

data class GroupConfirmUi(
    val title: String,
    val text: String,
    val confirmLabel: String,
)

class GroupViewModel(
    private val groupId: GroupId,
    private val friends: FriendsRepository,
    private val invites: InviteSharing,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
) : ViewModel() {
    private val mutableState = MutableStateFlow<GroupUiState?>(null)
    val state: StateFlow<GroupUiState?> = mutableState
    private val mutableOffline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = mutableOffline
    private val mutableGone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = mutableGone
    private val writes = WriteGuard(viewModelScope)

    private var group: FriendGroup? = null
    private var members: List<GroupMember> = emptyList()
    private var confirming = false
    private var notice: String? = null

    init {
        viewModelScope.launch {
            accounts.activeId.collect { load() }
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun askToGo() {
        confirming = true
        publish()
    }

    fun cancel() {
        confirming = false
        publish()
    }

    fun confirm() {
        val shown = group ?: return
        writes.launch {
            val owner = shown.ownerId == currentUser.id()
            reading { if (owner) friends.delete(shown.id) else friends.leave(shown.id) }
                .onSuccess { mutableGone.value = true }
                .onFailure {
                    confirming = false
                    notice = "Нет связи с сервером"
                    publish()
                }
        }
    }

    fun invite() {
        val shown = group ?: return
        writes.launch {
            val link = invites.pageAddress?.let { inviteLink(it, shown.inviteCode) }
            val invite = Invite(shown.name, shown.inviteCode, link)
            notice = reading { invites.share(invite) }.getOrNull()
            publish()
        }
    }

    private suspend fun load() {
        reading { friends.group(groupId)?.let { it to friends.members(it) } }
            .onSuccess { found ->
                if (found == null) {
                    mutableGone.value = true
                } else {
                    group = found.first
                    members = found.second
                    publish()
                }
                mutableOffline.value = false
            }.onFailure {
                mutableOffline.value = true
            }
    }

    private fun publish() {
        val shown = group ?: return
        val viewer = accounts.activeId.value
        val isOwner = shown.ownerId == viewer
        mutableState.value =
            GroupUiState(
                title = shown.name,
                code = shown.inviteCode,
                members =
                    members.map {
                        val friend = Friend(it.userId, it.displayName)
                        MemberRowUi(friend, it.isOwner, it.userId != viewer)
                    },
                isOwner = isOwner,
                confirming = if (confirming) confirmUi(isOwner) else null,
                notice = notice,
            )
    }

    private fun confirmUi(isOwner: Boolean) =
        if (isOwner) {
            GroupConfirmUi(
                "Удалить группу?",
                "Участники перестанут видеть визиты друг друга.",
                "Удалить",
            )
        } else {
            GroupConfirmUi(
                "Выйти из группы?",
                "Вы перестанете видеть визиты участников, а они — ваши.",
                "Выйти",
            )
        }
}
