package monster.greyde.kachalochka.ui.share

import androidx.compose.ui.graphics.ImageBitmap

/** Android's share sheet for a visit card drawn as a picture. */
interface CardSharing {
    /** False hides the share popup's picture choice. */
    val available: Boolean

    /** Shares the picture that [strips] make from top to bottom. */
    suspend fun share(strips: List<ImageBitmap>)
}

object UnavailableCardSharing : CardSharing {
    override val available = false

    override suspend fun share(strips: List<ImageBitmap>) = Unit
}
