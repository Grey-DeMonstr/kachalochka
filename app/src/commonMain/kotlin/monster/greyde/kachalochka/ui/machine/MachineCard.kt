package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.DISABLED_ALPHA
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.MachineThumbnail
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.friendColor
import monster.greyde.kachalochka.ui.visit.TagChip

/** Frame 7w: a machine's photo, name, tags, comment, last use and record, in any list. */
@Composable
fun MachineCard(
    card: MachineCardUi,
    tag: String,
    /** Given, a link button at the title's end hands on the card's suggestion. */
    onSuggestion: ((String) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val id = card.id.value
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .alpha(if (card.inVisit) DISABLED_ALPHA else 1f)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(tag),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            MachineThumbnail(card.photo, PhosphorIcons.Barbell, size = 58.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.name,
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        color = colors.onBackground,
                    )
                    val suggestion = card.suggestion
                    if (suggestion != null && onSuggestion != null) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onSuggestion(suggestion) }
                                .testTag("card-suggestion-$id"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                PhosphorIcons.LinkSimple,
                                strings().suggestedLink,
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (card.linkedWith.isNotEmpty()) {
                    LinkedMachines(card.linkedWith, "card-linked-$id")
                }
                if (card.inVisit) {
                    Text(
                        strings().alreadyInVisit,
                        modifier = Modifier.testTag("card-in-visit-$id"),
                        fontSize = 14.sp,
                        color = colors.secondary,
                    )
                }
                if (card.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        card.tags.forEach { TagChip(it, Modifier.testTag("card-tag-$id-$it")) }
                    }
                }
                if (card.comment.isNotEmpty()) {
                    Text(
                        card.comment,
                        modifier = Modifier.testTag("card-comment-$id"),
                        fontSize = 14.sp,
                        color = colors.onBackground.copy(alpha = 0.68f),
                    )
                }
                if (card.lastUsed != null || card.record != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val muted = colors.onBackground.copy(alpha = 0.52f)
                        card.lastUsed?.let {
                            Text(
                                it,
                                modifier = Modifier.testTag("card-last-$id"),
                                fontSize = 13.sp,
                                color = muted,
                            )
                            if (card.record != null) Text("·", fontSize = 13.sp, color = muted)
                        }
                        card.record?.let {
                            Icon(
                                PhosphorIcons.Trophy,
                                null,
                                tint = colors.secondary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                it,
                                modifier = Modifier.testTag("card-record-$id"),
                                fontSize = 13.sp,
                                color = muted,
                            )
                        }
                    }
                }
            }
        }
        Rule()
    }
}

/** A plain machine row, where a card's record and tags would not help the choice. */
@Composable
internal fun MachineRow(
    name: String,
    detail: String?,
    tag: String,
    photo: Photo? = null,
    linkedWith: List<LinkedMachineUi> = emptyList(),
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MachineThumbnail(photo, PhosphorIcons.Image)
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, color = colors.onBackground)
                if (linkedWith.isNotEmpty()) LinkedMachines(linkedWith, "$tag-linked")
                detail?.let {
                    Text(it, fontSize = 13.sp, color = colors.onBackground.copy(alpha = 0.52f))
                }
            }
        }
        Rule()
    }
}

/** A friend's avatar and name over their machines, with how many there are. */
@Composable
fun FriendSectionHeader(
    section: FriendSectionUi,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PersonAvatar(
                section.friend.userId,
                section.friend.displayName,
                section.friend.avatar,
                size = 26.dp,
                tint = section.color?.let { friendColor(it) },
            )
            Text(
                section.friend.displayName,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                color = colors.onBackground,
            )
            Text(
                section.cards.size.toString(),
                fontSize = 13.sp,
                color = colors.onBackground.copy(alpha = 0.5f),
            )
        }
        Rule()
    }
}
