package monster.greyde.kachalochka.ui.family

import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
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

internal class ChildrenViewModel(
    family: FamilyRepository,
    private val invites: InviteSharing,
    accounts: Accounts,
) : FamilyViewModel<ChildrenUiState>(family, accounts, ChildrenUiState()) {
    fun addChild() =
        writes.launch {
            reading { family.offer() }
                .onSuccess { code ->
                    val link = invites.pageAddress?.let { guardianLink(it, code) }
                    change { copy(code = code, link = link, notice = null) }
                }.onFailure { change { withFailure() } }
        }

    fun share() {
        val code = state.value.code ?: return
        val link = state.value.link
        writes.launch {
            val invite = Invite(AppStrings.current.guardianInviteMessage, code, link)
            val notice = reading { invites.share(invite) }.getOrNull()
            change { copy(notice = notice) }
        }
    }

    override fun peopleOf(family: Family) = family.children

    override fun ChildrenUiState.withPeople(people: List<FamilyMember>) =
        copy(children = people, offline = false)

    override fun ChildrenUiState.withOffline() = copy(offline = true)

    override fun ChildrenUiState.withRemoving(person: FamilyMember?) = copy(removing = person)

    override fun ChildrenUiState.withFailure() = copy(notice = AppStrings.current.offline)

    override fun removingOf(state: ChildrenUiState) = state.removing

    override suspend fun end(
        me: UserId,
        person: UserId,
    ) = family.end(person, me)
}
