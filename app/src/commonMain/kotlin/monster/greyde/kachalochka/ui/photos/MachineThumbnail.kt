package monster.greyde.kachalochka.ui.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.Thumbnail

/** A machine's cover [photo], or [icon] while it has none. */
@Composable
fun MachineThumbnail(
    photo: Photo?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(ControlShape)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        if (photo == null) {
            Thumbnail(icon, Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = photo,
                contentDescription = null,
                imageLoader = photoLoader(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag("machine-cover"),
            )
        }
    }
}
