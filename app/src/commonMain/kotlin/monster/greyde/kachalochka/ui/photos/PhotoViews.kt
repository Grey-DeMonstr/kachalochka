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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings

/**
 * A photo on screen; [model] is a saved `Photo` or the bytes of one not saved yet. A friend's
 * photo names its [owner]; [cover] marks the one standing for the machine.
 */
data class ShownPhoto(
    val key: String,
    val model: Any,
    val owner: Friend? = null,
    val cover: Boolean = false,
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
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEach { photo ->
            Box {
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
                photo.owner?.let {
                    PersonAvatar(
                        it.userId,
                        it.displayName,
                        it.avatar,
                        size = 24.dp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                }
                if (photo.cover && photos.size > 1) CoverBadge(Modifier.align(Alignment.TopEnd))
            }
        }
        launchers?.let { AddPhotoTile(it) }
    }
}

@Composable
private fun CoverBadge(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .padding(6.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(colors.background.copy(alpha = 0.8f))
            .testTag("photo-cover"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PhosphorIcons.Star,
            strings().coverPhoto,
            tint = colors.secondary,
            modifier = Modifier.size(16.dp),
        )
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
                PhosphorIcons.CameraPlus,
                strings().addPhoto,
                tint = colors.tertiary,
                modifier = Modifier.size(40.dp),
            )
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

/**
 * [photos] over the whole screen, a swipe apart, starting at [opened]. [onDelete] adds the button
 * removing an own photo, [onMakeCover] the one making any but the cover the machine's cover.
 */
@Composable
fun PhotoViewer(
    photos: List<ShownPhoto>,
    opened: ShownPhoto,
    onClose: () -> Unit,
    onDelete: ((ShownPhoto) -> Unit)? = null,
    onMakeCover: ((ShownPhoto) -> Unit)? = null,
) {
    val pager =
        rememberPagerState(photos.indexOfFirst { it.key == opened.key }.coerceAtLeast(0)) {
            photos.size
        }
    val photo = photos.getOrNull(pager.currentPage) ?: return
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
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { photos[it].key }) {
                AsyncImage(
                    model = photos[it].model,
                    contentDescription = strings().photo,
                    imageLoader = photoLoader(),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
                if (onDelete != null && photo.owner == null) {
                    SquareIconButton(
                        PhosphorIcons.Trash,
                        strings().delete,
                        { onDelete(photo) },
                        Modifier.testTag("delete-photo"),
                    )
                }
            }
            if (onMakeCover != null && !photo.cover) {
                OutlineButton(
                    strings().makeCover,
                    PhosphorIcons.Star,
                    { onMakeCover(photo) },
                    Modifier
                        .align(Alignment.BottomCenter)
                        .systemBarsPadding()
                        .padding(16.dp)
                        .fillMaxWidth()
                        .testTag("make-cover"),
                )
            }
        }
    }
}
