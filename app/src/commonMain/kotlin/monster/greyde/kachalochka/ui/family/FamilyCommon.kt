package monster.greyde.kachalochka.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.strings.strings

/** The other ends of the account's links, each with "Убрать"; [tag] starts every test tag. */
@Composable
internal fun FamilyList(
    people: List<FamilyMember>?,
    empty: String,
    tag: String,
    onRemove: (FamilyMember) -> Unit,
) {
    val faded = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    when {
        people == null ->
            Text(
                strings().loading,
                modifier = Modifier.testTag("$tag-loading"),
                fontSize = 15.sp,
                color = faded,
            )
        people.isEmpty() ->
            Text(empty, modifier = Modifier.testTag("$tag-empty"), fontSize = 15.sp, color = faded)
        else ->
            people.forEach { person ->
                FamilyRow(person, tag, onRemove)
                Rule()
            }
    }
}

@Composable
private fun FamilyRow(
    person: FamilyMember,
    tag: String,
    onRemove: (FamilyMember) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag("$tag-${person.userId.value}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(person.userId, person.displayName, person.avatar, size = 44.dp)
        Text(
            person.displayName,
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(
            onClick = { onRemove(person) },
            modifier = Modifier.testTag("$tag-remove-${person.userId.value}"),
        ) { Text(strings().removeLink) }
    }
}

@Composable
internal fun GuardianInviteDialog(
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings().guardianInviteTitle) },
        text = { Text(strings().guardianRights) },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                modifier = Modifier.testTag("guardian-invite-confirm"),
            ) { Text(strings().add) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("guardian-invite-cancel")) {
                Text(strings().cancel)
            }
        },
    )
}

@Composable
internal fun GuardianProblemDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("guardian-problem"),
        title = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("guardian-problem-ok")) {
                Text(strings().understood)
            }
        },
    )
}

/**
 * One side of the account's guardian links: read while the screen is open, reset when another
 * account becomes active, and ended after a question. The overrides are pure functions of their
 * arguments, because the first read starts while the base class is constructed.
 */
internal abstract class FamilyViewModel<S : Any>(
    protected val family: FamilyRepository,
    protected val accounts: Accounts,
    private val empty: S,
) : ViewModel() {
    private val mutableState = MutableStateFlow(empty)
    val state: StateFlow<S> = mutableState
    protected val writes = WriteGuard(viewModelScope)
    private var loading: Job? = null

    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                mutableState.value = empty
                load()
            }
        }
    }

    protected abstract fun peopleOf(family: Family): List<FamilyMember>

    protected abstract fun S.withPeople(people: List<FamilyMember>): S

    protected abstract fun S.withOffline(): S

    protected abstract fun S.withRemoving(person: FamilyMember?): S

    protected abstract fun S.withFailure(): S

    protected abstract fun removingOf(state: S): FamilyMember?

    protected abstract suspend fun end(
        me: UserId,
        person: UserId,
    )

    protected fun change(transform: S.() -> S) = mutableState.update { it.transform() }

    fun load() {
        loading?.cancel()
        loading = viewModelScope.launch { read() }
    }

    fun askToRemove(person: FamilyMember) = change { withRemoving(person) }

    fun cancelRemove() = change { withRemoving(null) }

    fun confirmRemove() {
        val person = removingOf(state.value) ?: return
        change { withRemoving(null) }
        val me = accounts.activeId.value ?: return
        writes.launch {
            reading { end(me, person.userId) }
                .onSuccess { read() }
                .onFailure { change { withFailure() } }
        }
    }

    protected suspend fun read() {
        reading { peopleOf(family.family()) }
            .onSuccess { found -> change { withPeople(found) } }
            .onFailure { change { withOffline() } }
    }
}
