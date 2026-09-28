package monster.greyde.kachalochka.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.friends.inviteCodeOf
import monster.greyde.kachalochka.core.domain.friends.typedInviteCode
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.memberCount

const val GROUP_NAME_LENGTH = 40

data class GroupsUiState(
    val groups: List<GroupRowUi>?,
    val offline: Boolean,
    val dialog: GroupsDialogUi?,
)

data class GroupRowUi(
    val id: GroupId,
    val name: String,
    val members: String,
)

enum class GroupsDialogKind { Create, Join }

data class GroupsDialogUi(
    val kind: GroupsDialogKind,
    val text: String,
    val canConfirm: Boolean,
    val error: String?,
)

class GroupsViewModel(
    private val friends: FriendsRepository,
    private val accounts: Accounts,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GroupsUiState(null, false, null))
    val state: StateFlow<GroupsUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var groups: List<GroupRowUi>? = null
    private var offline = false
    private var dialog: GroupsDialogUi? = null
    private var loading: Job? = null
    private var entered = false

    init {
        viewModelScope.launch {
            accounts.activeId.collect { load() }
        }
    }

    /**
     * The screen's mount effect: [init] already loads the first time, so this only reloads for a
     * return from a group, where the same view model survives but data may be stale.
     */
    fun enter() {
        if (entered) load() else entered = true
    }

    /** Cancels a load in flight, so a rapid account switch never races the one it starts. */
    fun load() {
        loading?.cancel()
        loading = viewModelScope.launch { doLoad() }
    }

    fun openCreate() = openDialog(GroupsDialogKind.Create)

    fun openJoin() = openDialog(GroupsDialogKind.Join)

    fun dismissDialog() {
        dialog = null
        publish()
    }

    fun type(text: String) {
        val current = dialog ?: return
        val typed =
            when (current.kind) {
                GroupsDialogKind.Create -> text.take(GROUP_NAME_LENGTH)
                GroupsDialogKind.Join -> typedInviteCode(text)
            }
        val canConfirm =
            when (current.kind) {
                GroupsDialogKind.Create -> typed.isNotBlank()
                GroupsDialogKind.Join -> inviteCodeOf(typed) != null
            }
        dialog = current.copy(text = typed, canConfirm = canConfirm, error = null)
        publish()
    }

    fun confirm(onOpen: (GroupId) -> Unit) {
        val current = dialog ?: return
        if (!current.canConfirm) return
        writes.launch {
            when (current.kind) {
                GroupsDialogKind.Create ->
                    reading { friends.create(current.text) }
                        .onSuccess { opened(it, onOpen) }
                        .onFailure { failed(current) }
                GroupsDialogKind.Join ->
                    reading { friends.join(current.text) }
                        .onSuccess { joined(it, current, onOpen) }
                        .onFailure { failed(current) }
            }
        }
    }

    private fun joined(
        id: GroupId?,
        current: GroupsDialogUi,
        onOpen: (GroupId) -> Unit,
    ) {
        if (id == null) notFound(current) else opened(id, onOpen)
    }

    private fun opened(
        id: GroupId,
        onOpen: (GroupId) -> Unit,
    ) {
        dialog = null
        publish()
        onOpen(id)
        load()
    }

    private fun notFound(current: GroupsDialogUi) {
        dialog = current.copy(error = "Приглашение не найдено")
        publish()
    }

    private fun failed(current: GroupsDialogUi) {
        dialog = current.copy(error = "Нет связи с сервером")
        publish()
    }

    private fun openDialog(kind: GroupsDialogKind) {
        dialog = GroupsDialogUi(kind, "", canConfirm = false, error = null)
        publish()
    }

    private suspend fun doLoad() {
        if (accounts.activeId.value == null) {
            groups = emptyList()
            offline = false
            publish()
            return
        }
        reading { friends.groups() }
            .onSuccess { found ->
                groups = found.map { GroupRowUi(it.id, it.name, memberCount(it.memberCount)) }
                offline = false
                publish()
            }.onFailure {
                offline = true
                publish()
            }
    }

    private fun publish() {
        mutableState.value = GroupsUiState(groups, offline, dialog)
    }
}
