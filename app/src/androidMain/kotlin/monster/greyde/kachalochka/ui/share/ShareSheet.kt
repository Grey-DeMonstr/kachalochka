package monster.greyde.kachalochka.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hands [image] to whatever app the user picks. The image waits in the cache, which the
 * `FileProvider` `androidApp` declares serves to that app.
 */
internal suspend fun shareImage(
    context: Context,
    image: ByteArray,
    name: String,
    type: String,
) {
    val file = File(context.cacheDir, "shared/$name")
    withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.writeBytes(image)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser passes the read grant on through the clip, and previews it.
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(
        Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
