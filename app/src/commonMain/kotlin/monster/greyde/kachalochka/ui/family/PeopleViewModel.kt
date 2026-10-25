package monster.greyde.kachalochka.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.family.FamilyFollower
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.friends.inviteCodeOf
import monster.greyde.kachalochka.core.domain.friends.typedInviteCode
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.friends.Invite
import monster.greyde.kachalochka.ui.friends.InviteSharing
import monster.greyde.kachalochka.ui.friends.guardianLink
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.strings.AppStrings

/** A new code for a child, with its [link] when the page address is known. */
data class ChildInvite(
    val code: String,
    val link: String?,
    val notice: String? = null,
)

/** A parent's code being typed; [error] answers the last try. */
data class GuardianEntry(
    val code: String = "",
    val canAdd: Boolean = false,
    val error: String? = null,
)

/** Both lists are null until the first read; [notice] answers the last failed change. */
data class PeopleUiState(
    val children: List<FamilyMember>? = null,
    val guardians: List<FamilyMember>? = null,
    val offline: Boolean = false,
    val removing: FamilyMember? = null,
    val notice: String? = null,
    val childInvite: ChildInvite? = null,
    val guardianEntry: GuardianEntry? = null,
)

/** The account's guardian links from both sides: read while the screen is open. */
internal class PeopleViewModel(
    private val family: FamilyRepository,
    private val follower: FamilyFollower,
    private val invites: InviteSharing,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PeopleUiState())
    val state: StateFlow<PeopleUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)
    private var loading: Job? = null

    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                mutableState.value = PeopleUiState()
                load()
            }
        }
    }

    private fun change(transform: PeopleUiState.() -> PeopleUiState) =
        mutableState.update { it.transform() }

    fun load() {
        loading?.cancel()
        loading = viewModelScope.launch { read() }
    }

    private suspend fun read() {
        reading { family.family() }
            .onSuccess { found ->
                change {
                    copy(
                        children = found.children,
                        guardians = found.guardians,
                        offline = false,
                    )
                }
                // Outside the write guard: it may wait out a sync pass, and taps meanwhile count.
                viewModelScope.launch { follow() }
            }.onFailure { change { copy(offline = true) } }
    }

    // The device's children follow what the server has just said; a new one needs a pass.
    private suspend fun follow() {
        reading { follower.follow() }.onSuccess { changed -> if (changed) sync.request() }
    }

    fun addChild() =
        writes.launch {
            reading { family.offer() }
                .onSuccess { code ->
                    val link = invites.pageAddress?.let { guardianLink(it, code) }
                    change { copy(childInvite = ChildInvite(code, link), notice = null) }
                }.onFailure { change { copy(notice = AppStrings.current.offline) } }
        }

    fun share() {
        val invite = state.value.childInvite ?: return
        writes.launch {
            val message =
                Invite(AppStrings.current.guardianInviteMessage, invite.code, invite.link)
            val notice = reading { invites.share(message) }.getOrNull()
            change { copy(childInvite = childInvite?.copy(notice = notice)) }
        }
    }

    fun closeChildInvite() = change { copy(childInvite = null) }

    fun addGuardian() = change { copy(guardianEntry = GuardianEntry()) }

    fun type(text: String) {
        val typed = typedInviteCode(text)
        change { copy(guardianEntry = GuardianEntry(typed, inviteCodeOf(typed) != null)) }
    }

    fun closeGuardianEntry() = change { copy(guardianEntry = null) }

    fun linkGuardian() {
        val code =
            state.value.guardianEntry
                ?.takeIf { it.canAdd }
                ?.code ?: return
        writes.launch {
            reading { family.accept(code) }
                .onSuccess { linked(it) }
                .onFailure { entryError(AppStrings.current.offline) }
        }
    }

    private suspend fun linked(outcome: Acceptance) {
        when (outcome) {
            is Acceptance.Linked -> {
                change { copy(guardianEntry = null) }
                read()
            }
            Acceptance.UnknownCode -> entryError(AppStrings.current.guardianCodeUnknown)
            Acceptance.OwnCode -> entryError(AppStrings.current.ownGuardianCode)
        }
    }

    private fun entryError(text: String) =
        change { copy(guardianEntry = guardianEntry?.copy(error = text)) }

    fun askToRemove(person: FamilyMember) = change { copy(removing = person) }

    fun cancelRemove() = change { copy(removing = null) }

    /** A child's link is ended as their parent, a parent's as their child. */
    fun confirmRemove() {
        val person = state.value.removing ?: return
        val child =
            state.value.children
                .orEmpty()
                .any { it.userId == person.userId }
        change { copy(removing = null) }
        val me = accounts.activeId.value ?: return
        writes.launch {
            reading { if (child) family.end(person.userId, me) else family.end(me, person.userId) }
                .onSuccess { read() }
                .onFailure { change { copy(notice = AppStrings.current.offline) } }
        }
    }
}
