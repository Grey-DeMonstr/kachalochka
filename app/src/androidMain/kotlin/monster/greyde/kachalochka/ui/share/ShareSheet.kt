package monster.greyde.kachalochka.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Hands one [image] to whatever app the user picks. */
internal suspend fun shareImage(
    context: Context,
    image: ByteArray,
    name: String,
    type: String,
) = shareImages(context, listOf(image), listOf(name), type)

/**
 * Hands [images], saved under [names], to whatever app the user picks. The images wait in the
 * cache, which the `FileProvider` `androidApp` declares serves to that app.
 */
internal suspend fun shareImages(
    context: Context,
    images: List<ByteArray>,
    names: List<String>,
    type: String,
) {
    val files = names.map { File(context.cacheDir, "shared/$it") }
    withContext(Dispatchers.IO) {
        files.zip(images).forEach { (file, image) ->
            file.parentFile?.mkdirs()
            file.writeBytes(image)
        }
    }
    val uris =
        files.map {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.photos",
                it,
            )
        }
    val send =
        if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
        }.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser passes the read grant on through the clip, and previews it.
    send.clipData =
        ClipData.newRawUri(null, uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
    context.startActivity(
        Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
