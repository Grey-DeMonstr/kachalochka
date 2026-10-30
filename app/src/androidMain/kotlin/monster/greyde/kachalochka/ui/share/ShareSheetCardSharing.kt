package monster.greyde.kachalochka.ui.share

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ShareSheetCardSharing(
    private val context: Context,
) : CardSharing {
    override val available = true

    override suspend fun share(card: ImageBitmap) {
        val png =
            withContext(Dispatchers.Default) {
                val bitmap = card.asAndroidBitmap()
                // A layer's picture can live on the GPU, out of reach of the PNG encoder.
                val readable =
                    if (bitmap.config == Bitmap.Config.HARDWARE) {
                        bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        bitmap
                    }
                ByteArrayOutputStream()
                    .also { readable.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    .toByteArray()
            }
        shareImage(context, png, "visit.png", "image/png")
    }
}
