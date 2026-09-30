package monster.greyde.kachalochka.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.monogram
import monster.greyde.kachalochka.ui.photos.avatarPhoto
import monster.greyde.kachalochka.ui.photos.photoLoader

/**
 * [owner]'s [avatar] in a circle, over the initial of [name], which stays while the picture loads
 * or when there is none. [tint], a friend's calendar colour, draws the initial's circle.
 */
@Composable
fun PersonAvatar(
    owner: UserId?,
    name: String,
    avatar: Avatar,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    accent: Boolean = true,
    tint: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val filled = accent || tint != null
    val ring = tint ?: if (accent) colors.primary else colors.onBackground.copy(alpha = 0.22f)
    val fill = if (filled) ring.copy(alpha = 0.16f) else Color.Transparent
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            monogram(name),
            fontSize = (size.value * 0.4f).sp,
            color = tint ?: if (filled) colors.onPrimaryContainer else colors.onBackground,
        )
        val model = avatar.photo?.let { avatarPhoto(owner, it) } ?: avatar.picture
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                imageLoader = photoLoader(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().testTag("avatar-image"),
            )
        }
    }
}
