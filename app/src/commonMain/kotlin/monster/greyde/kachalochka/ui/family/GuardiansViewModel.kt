package monster.greyde.kachalochka.ui.family

import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.friends.inviteCodeOf
import monster.greyde.kachalochka.core.domain.friends.typedInviteCode
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.strings.AppStrings

/** [guardians] is null until the first read; [error] answers the last add or removal. */
data class GuardiansUiState(
    val guardians: List<FamilyMember>? = null,
    val offline: Boolean = false,
    val code: String = "",
    val canAdd: Boolean = false,
    val error: String? = null,
    val removing: FamilyMember? = null,
)

internal class GuardiansViewModel(
    family: FamilyRepository,
    accounts: Accounts,
) : FamilyViewModel<GuardiansUiState>(family, accounts, GuardiansUiState()) {
    fun type(text: String) {
        val typed = typedInviteCode(text)
        change { copy(code = typed, canAdd = inviteCodeOf(typed) != null, error = null) }
    }

    fun add() {
        val code = state.value.code.takeIf { state.value.canAdd } ?: return
        writes.launch {
            reading { family.accept(code) }
                .onSuccess { accepted(it) }
                .onFailure { change { withFailure() } }
        }
    }

    private suspend fun accepted(outcome: Acceptance) {
        when (outcome) {
            is Acceptance.Linked -> {
                change { copy(code = "", canAdd = false, error = null) }
                read()
            }
            Acceptance.UnknownCode ->
                change { copy(error = AppStrings.current.guardianCodeUnknown) }
            Acceptance.OwnCode -> change { copy(error = AppStrings.current.ownGuardianCode) }
        }
    }

    override fun peopleOf(family: Family) = family.guardians

    override fun GuardiansUiState.withPeople(people: List<FamilyMember>) =
        copy(guardians = people, offline = false)

    override fun GuardiansUiState.withOffline() = copy(offline = true)

    override fun GuardiansUiState.withRemoving(person: FamilyMember?) = copy(removing = person)

    override fun GuardiansUiState.withFailure() = copy(error = AppStrings.current.offline)

    override fun removingOf(state: GuardiansUiState) = state.removing

    override suspend fun end(
        me: UserId,
        person: UserId,
    ) = family.end(me, person)
}
