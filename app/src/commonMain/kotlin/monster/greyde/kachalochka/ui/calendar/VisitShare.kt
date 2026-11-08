package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.plans.PlacementNoticeText
import monster.greyde.kachalochka.ui.strings.strings

private val PICTURE_WIDTH = 400.dp
private const val PICTURE_DENSITY = 2.7f
private val CHOICE_ICON = 20.dp
private val CHOICE_GAP = 12.dp

/** The share popup [viewModel] offers, and the card it draws off screen for a picture. */
@Composable
internal fun VisitShareDialog(viewModel: VisitShareViewModel) {
    val state by viewModel.state.collectAsState()
    if (state.offered != null) SharePopup(state, viewModel)
    state.picture?.let { CardPicture(it, viewModel::pictureDrawn) }
}

/** What the last text share of the visit on [day] said; a tap hides it. */
@Composable
internal fun VisitShareNotice(
    viewModel: VisitShareViewModel,
    day: CalendarDay,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val notice = state.notice?.takeIf { it.day == day } ?: return
    PlacementNoticeText(notice.text, viewModel::dismissNotice, modifier, "share-notice")
}

@Composable
private fun SharePopup(
    state: VisitShareUi,
    viewModel: VisitShareViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancel,
        title = { Text(strings().share) },
        text = {
            Column {
                ShareChoice(
                    strings().asText,
                    PhosphorIcons.ChatTeardropText,
                    "share-as-text",
                    viewModel::shareText,
                )
                // Under the text choice, as it changes only the text.
                if (state.canGroupByTag) {
                    GroupByTagBox(state.groupByTag, viewModel::toggleGroupByTag)
                }
                if (state.canShareImage) {
                    ShareChoice(
                        strings().asImage,
                        PhosphorIcons.Image,
                        "share-as-image",
                        viewModel::shareImage,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::cancel, modifier = Modifier.testTag("share-cancel")) {
                Text(strings().cancel)
            }
        },
    )
}

@Composable
private fun ShareChoice(
    text: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHOICE_GAP),
    ) {
        Icon(icon, null, Modifier.size(CHOICE_ICON), tint = MaterialTheme.colorScheme.onSurface)
        Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
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
            .padding(start = CHOICE_ICON + CHOICE_GAP, top = 4.dp, bottom = 8.dp)
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

/**
 * Draws [card] off screen at a fixed width and density, so the picture is the same on every
 * phone, and gives that picture to [onDrawn] once the avatar is in it.
 */
@Composable
private fun CardPicture(
    card: VisitCardUi,
    onDrawn: (ImageBitmap) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    var avatarSettled by remember { mutableStateOf(false) }
    val recorded = remember { CompletableDeferred<Unit>() }
    val screen = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(PICTURE_DENSITY, screen.fontScale)) {
        Box(
            Modifier
                .clearAndSetSemantics {}
                .layout { measurable, _ ->
                    val width = PICTURE_WIDTH.roundToPx()
                    val placeable = measurable.measure(Constraints.fixedWidth(width))
                    layout(0, 0) { placeable.place(0, 0) }
                }.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    if (avatarSettled) recorded.complete(Unit)
                }.background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            SharedVisitCard(card, onAvatarSettled = { avatarSettled = true })
        }
    }
    LaunchedEffect(Unit) {
        recorded.await()
        onDrawn(layer.toImageBitmap())
    }
}
