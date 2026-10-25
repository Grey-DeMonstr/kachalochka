package monster.greyde.kachalochka.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hands the photo to whatever app the user picks. The photo waits in the cache, which the
 * `FileProvider` `androidApp` declares serves to that app.
 */
class ShareSheetPhotoSharing(
    private val context: Context,
) : PhotoSharing {
    override suspend fun share(jpeg: ByteArray) {
        val file = File(context.cacheDir, "shared/photo.jpg")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeBytes(jpeg)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The chooser passes the read grant on through the clip, and previews it.
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(
            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
