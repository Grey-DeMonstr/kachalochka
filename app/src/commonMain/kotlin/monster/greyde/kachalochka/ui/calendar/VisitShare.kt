package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.plans.PlacementNoticeText
import monster.greyde.kachalochka.ui.share.ChoiceTextIndent
import monster.greyde.kachalochka.ui.share.OffScreenPicture
import monster.greyde.kachalochka.ui.share.SharePopup
import monster.greyde.kachalochka.ui.strings.strings

/** The share popup [viewModel] offers, and the card it draws off screen for a picture. */
@Composable
internal fun VisitShareDialog(viewModel: VisitShareViewModel) {
    val state by viewModel.state.collectAsState()
    if (state.offered != null) {
        SharePopup(
            state.canShareImage,
            viewModel::shareText,
            viewModel::shareImage,
            viewModel::cancel,
        ) {
            // Under the text choice, as it changes only the text.
            if (state.canGroupByTag) {
                GroupByTagBox(state.groupByTag, viewModel::toggleGroupByTag)
            }
        }
    }
    state.picture?.let { CardPicture(it, it.date.orEmpty(), viewModel::pictureDrawn) }
}

/** What the last text share of the card tagged [card] said; a tap hides it. */
@Composable
internal fun VisitShareNotice(
    viewModel: VisitShareViewModel,
    card: String,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val notice = state.notice?.takeIf { it.card == card } ?: return
    PlacementNoticeText(notice.text, viewModel::dismissNotice, modifier, "share-notice")
}

@Composable
private fun GroupByTagBox(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = ChoiceTextIndent, top = 4.dp, bottom = 8.dp)
            .testTag("share-group-by-tag"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            strings().groupByTag,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

/** Draws [card] off screen and gives its picture to [onDrawn] once the avatar is in it. */
@Composable
internal fun CardPicture(
    card: VisitCardUi,
    caption: String,
    onDrawn: (List<ImageBitmap>) -> Unit,
) {
    var avatarSettled by remember { mutableStateOf(false) }
    OffScreenPicture(avatarSettled, onDrawn) {
        SharedVisitCard(card, caption, onAvatarSettled = { avatarSettled = true })
    }
}
