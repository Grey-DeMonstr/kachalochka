package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.theme.friendColor

/** The feed's people as avatars that hide and show their cards, wrapped onto more rows. */
@Composable
internal fun FeedPeopleRow(
    people: List<FeedPersonUi>,
    onToggle: (UserId) -> Unit,
) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(start = 11.dp, top = 12.dp, end = 11.dp, bottom = 6.dp)
            .testTag("feed-people"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
    val tag = "feed-person-${id.value}"
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .toggleable(item.shown, role = Role.Checkbox) { onToggle(id) }
            .alpha(if (item.shown) 1f else 0.5f)
            .padding(vertical = 4.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            val ring = if (item.shown) tint else colors.onBackground.copy(alpha = 0.18f)
            PersonAvatar(
                person.userId,
                person.name,
                person.avatar,
                Modifier.border(2.dp, ring, CircleShape).padding(4.dp),
                size = 40.dp,
                tint = friendTint,
            )
            if (item.shown) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(2.dp, 2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(tint)
                        .border(2.dp, colors.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PhosphorIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = colors.background,
                    )
                }
            }
        }
        Text(
            person.name.substringBefore(' '),
            modifier = Modifier.testTag("$tag-name"),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.onBackground.copy(alpha = if (item.shown) 1f else 0.6f),
        )
    }
}
