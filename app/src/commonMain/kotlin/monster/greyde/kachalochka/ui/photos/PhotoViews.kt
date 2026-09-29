package monster.greyde.kachalochka.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings

/** A photo on screen; [model] is a saved `Photo` or the bytes of one not saved yet. */
data class ShownPhoto(
    val key: String,
    val model: Any,
)

private val ThumbnailShape = RoundedCornerShape(10.dp)

/** The photos in a scrolling row, then the tile adding one when [launchers] can. */
@Composable
fun PhotoStrip(
    photos: List<ShownPhoto>,
    launchers: PhotoLaunchers?,
    onOpen: (ShownPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (photos.isEmpty() && launchers == null) return
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEach { photo ->
            AsyncImage(
                model = photo.model,
                contentDescription = strings().photo,
                imageLoader = photoLoader(),
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(100.dp)
                        .clip(ThumbnailShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpen(photo) }
                        .testTag("photo-thumbnail"),
            )
        }
        launchers?.let { AddPhotoTile(it) }
    }
}

@Composable
private fun AddPhotoTile(launchers: PhotoLaunchers) {
    val colors = MaterialTheme.colorScheme
    var choosing by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .size(100.dp)
                .clip(ThumbnailShape)
                .border(1.dp, colors.primary, ThumbnailShape)
                .background(colors.primary.copy(alpha = 0.10f))
                .clickable { choosing = true }
                .testTag("machine-photo"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                PhosphorIcons.Camera,
                null,
                tint = colors.tertiary,
                modifier = Modifier.size(30.dp),
            )
            Text(strings().addPhoto, fontSize = 13.sp, color = colors.tertiary)
        }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            DropdownMenuItem(
                text = { Text(strings().takePhoto) },
                onClick = {
                    choosing = false
                    launchers.takePhoto()
                },
                modifier = Modifier.testTag("take-photo"),
            )
            DropdownMenuItem(
                text = { Text(strings().fromGallery) },
                onClick = {
                    choosing = false
                    launchers.pickPhoto()
                },
                modifier = Modifier.testTag("pick-photo"),
            )
        }
    }
}

/** [photo] over the whole screen; [onDelete] adds the button removing it. */
@Composable
fun PhotoViewer(
    photo: ShownPhoto,
    onClose: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("photo-viewer"),
        ) {
            AsyncImage(
                model = photo.model,
                contentDescription = strings().photo,
                imageLoader = photoLoader(),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.fillMaxWidth().systemBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SquareIconButton(
                    PhosphorIcons.ArrowLeft,
                    strings().back,
                    onClose,
                    Modifier.testTag("close-photo"),
                )
                Spacer(Modifier.weight(1f))
                if (onDelete != null) {
                    SquareIconButton(
                        PhosphorIcons.Trash,
                        strings().delete,
                        onDelete,
                        Modifier.testTag("delete-photo"),
                    )
                }
            }
        }
    }
}
