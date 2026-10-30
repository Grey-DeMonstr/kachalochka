package monster.greyde.kachalochka.ui.share

import androidx.compose.ui.graphics.ImageBitmap

/** Android's share sheet for a visit card drawn as a picture. */
interface CardSharing {
    /** False hides the card menu's share item. */
    val available: Boolean

    suspend fun share(card: ImageBitmap)
}

object UnavailableCardSharing : CardSharing {
    override val available = false

    override suspend fun share(card: ImageBitmap) = Unit
}
