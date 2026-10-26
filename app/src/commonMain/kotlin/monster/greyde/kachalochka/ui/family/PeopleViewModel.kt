package monster.greyde.kachalochka.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.catching
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
import monster.greyde.kachalochka.ui.friends.trainerLink
import monster.greyde.kachalochka.ui.strings.AppStrings

/** A parent and a child are linked as family; a trainer and a trainee by coaching. */
enum class LinkKind { Family, Coaching }

/** How a person on the screen is linked to the account. */
enum class Relation(
    val kind: LinkKind,
) {
    Child(LinkKind.Family),
    Guardian(LinkKind.Family),
    Trainee(LinkKind.Coaching),
    Trainer(LinkKind.Coaching),
}

/** A new code of the account for a child or a trainee, with its [link] when the page is known. */
data class CodeOffer(
    val kind: LinkKind,
    val code: String,
    val link: String?,
    val notice: String? = null,
)

/** A parent's or a trainer's code being typed; [error] answers the last try. */
data class CodeEntry(
    val kind: LinkKind,
    val code: String = "",
    val canAdd: Boolean = false,
    val error: String? = null,
)

/** The person whose link a question asks to end, and how they are linked. */
data class Removal(
    val person: FamilyMember,
    val relation: Relation,
)

/** Every list is null until the first read; [notice] answers the last failed change. */
data class PeopleUiState(
    val children: List<FamilyMember>? = null,
    val guardians: List<FamilyMember>? = null,
    val trainees: List<FamilyMember>? = null,
    val trainers: List<FamilyMember>? = null,
    val offline: Boolean = false,
    val removing: Removal? = null,
    val notice: String? = null,
    val offer: CodeOffer? = null,
    val entry: CodeEntry? = null,
)

/** The account's guardian and coaching links from both sides: read while the screen is open. */
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
        catching { family.family() }
            .onSuccess { found ->
                change {
                    copy(
                        children = found.children,
                        guardians = found.guardians,
                        trainees = found.trainees,
                        trainers = found.trainers,
                        offline = false,
                    )
                }
                // Outside the write guard: it may wait out a sync pass, and taps meanwhile count.
                viewModelScope.launch { follow() }
            }.onFailure { change { copy(offline = true) } }
    }

    // The device's children and trainees follow what the server has just said; a new one needs
    // a pass.
    private suspend fun follow() {
        catching { follower.follow() }.onSuccess { changed -> if (changed) sync.request() }
    }

    fun addChild() = offer(LinkKind.Family)

    fun addTrainee() = offer(LinkKind.Coaching)

    private fun offer(kind: LinkKind) =
        writes.launch {
            val parental = kind == LinkKind.Family
            val linkOf = if (parental) ::guardianLink else ::trainerLink
            catching { if (parental) family.offer() else family.offerCoaching() }
                .onSuccess { code ->
                    val link = invites.pageAddress?.let { linkOf(it, code) }
                    change { copy(offer = CodeOffer(kind, code, link), notice = null) }
                }.onFailure { change { copy(notice = AppStrings.current.offline) } }
        }

    fun share() {
        val offer = state.value.offer ?: return
        val strings = AppStrings.current
        val call =
            if (offer.kind == LinkKind.Family) {
                strings.guardianInviteMessage
            } else {
                strings.trainerInviteMessage
            }
        writes.launch {
            val notice =
                catching {
                    invites.share(
                        Invite(call, offer.code, offer.link),
                    )
                }.getOrNull()
            change { copy(offer = this.offer?.copy(notice = notice)) }
        }
    }

    fun closeOffer() = change { copy(offer = null) }

    fun addGuardian() = change { copy(entry = CodeEntry(LinkKind.Family)) }

    fun addTrainer() = change { copy(entry = CodeEntry(LinkKind.Coaching)) }

    fun type(text: String) {
        val typed = typedInviteCode(text)
        change {
            copy(
                entry =
                    entry?.copy(
                        code = typed,
                        canAdd = inviteCodeOf(typed) != null,
                        error = null,
                    ),
            )
        }
    }

    fun closeEntry() = change { copy(entry = null) }

    /** Links the account to whoever gave the typed code: its typing is the account's consent. */
    fun link() {
        val entry = state.value.entry?.takeIf { it.canAdd } ?: return
        writes.launch {
            catching {
                if (entry.kind == LinkKind.Family) {
                    family.accept(entry.code)
                } else {
                    family.acceptTrainer(entry.code)
                }
            }.onSuccess { linked(it) }
                .onFailure { entryError(AppStrings.current.offline) }
        }
    }

    private suspend fun linked(outcome: Acceptance) {
        when (outcome) {
            is Acceptance.Linked -> {
                change { copy(entry = null) }
                read()
            }
            Acceptance.UnknownCode -> entryError(AppStrings.current.guardianCodeUnknown)
            Acceptance.OwnCode -> entryError(AppStrings.current.ownGuardianCode)
        }
    }

    private fun entryError(text: String) = change { copy(entry = entry?.copy(error = text)) }

    fun askToRemove(
        person: FamilyMember,
        relation: Relation,
    ) = change { copy(removing = Removal(person, relation)) }

    fun cancelRemove() = change { copy(removing = null) }

    /** Each link is ended naming both of its sides, the account being one of them. */
    fun confirmRemove() {
        val removal = state.value.removing ?: return
        change { copy(removing = null) }
        val me = accounts.activeId.value ?: return
        val other = removal.person.userId
        writes.launch {
            catching {
                when (removal.relation) {
                    Relation.Child -> family.end(other, me)
                    Relation.Guardian -> family.end(me, other)
                    Relation.Trainee -> family.endCoaching(other, me)
                    Relation.Trainer -> family.endCoaching(me, other)
                }
            }.onSuccess { read() }
                .onFailure { change { copy(notice = AppStrings.current.offline) } }
        }
    }
}
