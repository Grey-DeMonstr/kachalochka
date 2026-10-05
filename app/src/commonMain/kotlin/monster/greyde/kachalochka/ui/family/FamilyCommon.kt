package monster.greyde.kachalochka.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.Rule
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
