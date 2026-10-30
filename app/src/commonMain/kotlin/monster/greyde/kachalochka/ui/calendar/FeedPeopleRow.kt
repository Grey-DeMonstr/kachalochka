package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.theme.friendColor

/** The feed's people as avatars that hide and show their cards, in one row that scrolls. */
@Composable
internal fun FeedPeopleRow(
    people: List<FeedPersonUi>,
    onToggle: (UserId) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 4.dp)
            .testTag("feed-people"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        people.forEach { FeedPerson(it, onToggle) }
    }
}

@Composable
private fun FeedPerson(
    item: FeedPersonUi,
    onToggle: (UserId) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val person = item.person
    val id = checkNotNull(person.userId)
    val friendTint = item.color?.let { friendColor(it) }
    val tint = friendTint ?: colors.primary
    val ring = if (item.shown) tint else colors.onBackground.copy(alpha = 0.18f)
    Box(
        Modifier
            .toggleable(
                item.shown,
                interactionSource = null,
                indication = ripple(bounded = false),
                role = Role.Checkbox,
            ) { onToggle(id) }
            .alpha(if (item.shown) 1f else 0.5f)
            .semantics { contentDescription = person.name }
            .testTag("feed-person-${id.value}"),
    ) {
        PersonAvatar(
            person.userId,
            person.name,
            person.avatar,
            Modifier.border(1.2.dp, ring, CircleShape).padding(2.4.dp),
            size = 24.dp,
            tint = friendTint,
        )
        if (item.shown) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(1.2.dp, 1.2.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(tint)
                    .border(1.2.dp, colors.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(7.dp),
                    tint = colors.background,
                )
            }
        }
    }
}
