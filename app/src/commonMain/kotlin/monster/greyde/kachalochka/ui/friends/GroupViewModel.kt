package monster.greyde.kachalochka.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.friends.GroupMember
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.strings.AppStrings

data class GroupUiState(
    val title: String,
    val code: String,
    val members: List<MemberRowUi>,
    val isOwner: Boolean,
    val confirming: GroupConfirmUi?,
    val notice: String?,
    val colorPicker: UserId?,
)

data class MemberRowUi(
    val friend: Friend,
    val owner: Boolean,
    val opens: Boolean,
    val color: Int?,
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
    private val colorStore: FriendColorStore,
    private val sync: SyncTrigger,
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
    private var colors: Map<UserId, Int> = emptyMap()

    /** Whose palette [colors] is; another account's is never kept when a read fails. */
    private var colorsFor: UserId? = null
    private var colorPicker: UserId? = null

    private var spokenIn = AppStrings.current

    /** Reads again when the language changed while another screen was open. */
    fun speak() {
        if (AppStrings.current == spokenIn) return
        spokenIn = AppStrings.current
        viewModelScope.launch { load() }
    }

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
                    notice = AppStrings.current.offline
                    publish()
                }
        }
    }

    fun pickColor(member: UserId) {
        colorPicker = member
        publish()
    }

    fun dismissColor() {
        colorPicker = null
        publish()
    }

    fun chooseColor(index: Int) {
        val member = colorPicker ?: return
        colorPicker = null
        publish()
        writes.launch {
            val owner = currentUser.id() ?: return@launch
            reading { colorStore.set(owner, member, index) }
                .onSuccess {
                    colors = colors + (member to index)
                    sync.request()
                }.onFailure { notice = AppStrings.current.offline }
            publish()
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
                    val owner = currentUser.id()
                    val kept = if (owner == colorsFor) colors else emptyMap()
                    colors = reading { colorsOf(owner, found.second) }.getOrDefault(kept)
                    colorsFor = owner
                    publish()
                }
                mutableOffline.value = false
            }.onFailure {
                mutableOffline.value = true
            }
    }

    private suspend fun colorsOf(
        owner: UserId?,
        members: List<GroupMember>,
    ): Map<UserId, Int> {
        if (owner == null) return emptyMap()
        return colorStore.colorsFor(owner, members.map { it.userId }.filter { it != owner })
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
                        val mate = it.userId != viewer
                        MemberRowUi(
                            friend,
                            it.isOwner,
                            mate,
                            if (mate) colors[it.userId] else null,
                        )
                    },
                isOwner = isOwner,
                confirming = if (confirming) confirmUi(isOwner) else null,
                notice = notice,
                colorPicker = colorPicker,
            )
    }

    private fun confirmUi(isOwner: Boolean) =
        if (isOwner) {
            GroupConfirmUi(
                AppStrings.current.deleteGroupTitle,
                AppStrings.current.deleteGroupText,
                AppStrings.current.delete,
            )
        } else {
            GroupConfirmUi(
                AppStrings.current.leaveGroupTitle,
                AppStrings.current.leaveGroupText,
                AppStrings.current.leave,
            )
        }
}
