package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.theme.friendColor

/** The feed's people in one row that scrolls; a tap shows a person alone or everyone again. */
@Composable
internal fun FeedPeopleRow(
    people: List<FeedPersonUi>,
    onSelect: (UserId) -> Unit,
) {
    val anySelected = people.any { it.selected }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 4.dp)
            .testTag("feed-people"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        people.forEach { item ->
            val person = item.person
            val id = checkNotNull(person.userId)
            PersonAvatar(
                person.userId,
                person.name,
                person.avatar,
                Modifier
                    .selectable(
                        item.selected,
                        interactionSource = null,
                        indication = ripple(bounded = false),
                    ) { onSelect(id) }
                    .alpha(if (anySelected && !item.selected) 0.4f else 1f)
                    .semantics { contentDescription = person.name }
                    .testTag("feed-person-${id.value}"),
                size = 48.dp,
                tint = item.color?.let { friendColor(it) },
            )
        }
    }
}
