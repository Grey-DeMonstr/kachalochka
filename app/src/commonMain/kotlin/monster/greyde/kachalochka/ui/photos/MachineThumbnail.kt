package monster.greyde.kachalochka.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings

/**
 * A machine's cover [photo], or [icon] while it has none. A thumbnail that opens the machine's
 * settings shows a gear over the photo, or in place of [icon].
 */
@Composable
fun MachineThumbnail(
    photo: Photo?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    onOpenSettings: (() -> Unit)? = null,
) {
    val tap = onOpenSettings?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Box(modifier.size(size).clip(ControlShape).then(tap)) {
        when {
            photo != null -> {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    imageLoader = photoLoader(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag("machine-cover"),
                )
                if (onOpenSettings != null) SettingsBadge(Modifier.align(Alignment.BottomEnd))
            }
            onOpenSettings != null ->
                Thumbnail(PhosphorIcons.Gear, Modifier.fillMaxSize().testTag("settings-gear"))
            else -> Thumbnail(icon, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SettingsBadge(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .padding(3.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(colors.background.copy(alpha = 0.8f))
            .testTag("settings-gear"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PhosphorIcons.Gear,
            strings().settings,
            tint = colors.onBackground,
            modifier = Modifier.size(12.dp),
        )
    }
}
