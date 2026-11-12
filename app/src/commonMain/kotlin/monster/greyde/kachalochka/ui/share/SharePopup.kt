package monster.greyde.kachalochka.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings

private val PICTURE_WIDTH = 400.dp
private const val PICTURE_DENSITY = 2.7f
private val CHOICE_ICON = 20.dp
private val CHOICE_GAP = 12.dp

/** The indent of a box under the "As text" row, so it lines up with that row's text. */
internal val ChoiceTextIndent = CHOICE_ICON + CHOICE_GAP

/** "As text", and "As image" when [canShareImage]; [underText] shows under the text row. */
@Composable
internal fun SharePopup(
    canShareImage: Boolean,
    onText: () -> Unit,
    onImage: () -> Unit,
    onCancel: () -> Unit,
    underText: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings().share) },
        text = {
            Column {
                ShareChoice(
                    strings().asText,
                    PhosphorIcons.ChatTeardropText,
                    "share-as-text",
                    onText,
                )
                underText()
                if (canShareImage) {
                    ShareChoice(strings().asImage, PhosphorIcons.Image, "share-as-image", onImage)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("share-cancel")) {
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

/**
 * Draws [content] off screen at a fixed width and density, so the picture is the same on every
 * phone, and gives that picture to [onDrawn] once [settled] is true.
 */
@Composable
internal fun OffScreenPicture(
    settled: Boolean,
    onDrawn: (ImageBitmap) -> Unit,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
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
                    if (settled) recorded.complete(Unit)
                }.background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            content()
        }
    }
    LaunchedEffect(Unit) {
        recorded.await()
        onDrawn(layer.toImageBitmap())
    }
}
