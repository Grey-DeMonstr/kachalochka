package monster.greyde.kachalochka.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ShareSheetCardSharing(
    private val context: Context,
) : CardSharing {
    override val available = true

    override suspend fun share(strips: List<ImageBitmap>) {
        val png =
            withContext(Dispatchers.Default) {
                val picture =
                    Bitmap.createBitmap(
                        strips.first().width,
                        strips.sumOf { it.height },
                        Bitmap.Config.ARGB_8888,
                    )
                val canvas = Canvas(picture)
                var top = 0f
                strips.forEach { strip ->
                    // A layer's picture lives on the GPU, out of reach of this canvas.
                    val readable = strip.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                    canvas.drawBitmap(readable, 0f, top, null)
                    top += strip.height
                }
                ByteArrayOutputStream()
                    .also { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    .toByteArray()
            }
        shareImage(context, png, "visit.png", "image/png")
    }
}
