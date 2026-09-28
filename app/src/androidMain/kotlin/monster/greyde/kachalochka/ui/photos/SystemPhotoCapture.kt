package monster.greyde.kachalochka.ui.photos

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

private const val LOG_TAG = "Kachalochka"
private const val LONG_EDGE = 1600
private const val JPEG_QUALITY = 85

/**
 * The system camera and photo picker. The camera writes into the cache through the `FileProvider`
 * `androidApp` declares. The manifest must not declare the camera permission: the system camera
 * would then require it.
 */
class SystemPhotoCapture(
    private val context: Context,
) : PhotoCapture {
    @Composable
    override fun rememberLaunchers(onPhoto: (ByteArray) -> Unit): PhotoLaunchers {
        val scope = rememberCoroutineScope()
        val deliver by rememberUpdatedState(onPhoto)
        val target = remember { captureTarget() }
        val shrink = { uri: Uri ->
            scope.launch {
                val jpeg = withContext(Dispatchers.IO) { shrunkJpeg(context.contentResolver, uri) }
                jpeg?.let(deliver)
            }
            Unit
        }
        val camera =
            rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
                if (taken) shrink(target)
            }
        val gallery =
            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                uri?.let(shrink)
            }
        return remember(camera, gallery) {
            PhotoLaunchers(
                takePhoto = {
                    try {
                        camera.launch(target)
                    } catch (missing: ActivityNotFoundException) {
                        Log.w(LOG_TAG, "No camera app", missing)
                    }
                },
                pickPhoto = {
                    gallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        }
    }

    private fun captureTarget(): Uri {
        val file = File(context.cacheDir, "camera/capture.jpg")
        file.parentFile?.mkdirs()
        return FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    }
}

/** The picture at [uri], upright, its long edge at most [LONG_EDGE]; null if it cannot be read. */
internal fun shrunkJpeg(
    resolver: ContentResolver,
    uri: Uri,
): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = max(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    val sampled = BitmapFactory.Options()
    sampled.inSampleSize = 1
    while (longest / (sampled.inSampleSize * 2) >= LONG_EDGE) sampled.inSampleSize *= 2
    val decoded =
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, sampled) }
            ?: return null
    val matrix = Matrix()
    val scale = LONG_EDGE.toFloat() / max(decoded.width, decoded.height)
    if (scale < 1f) matrix.postScale(scale, scale)
    matrix.postRotate(rotationOf(resolver, uri).toFloat())
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    return ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
}

// Cameras store the sensor's picture and say in EXIF how to turn it.
private fun rotationOf(
    resolver: ContentResolver,
    uri: Uri,
): Int {
    val orientation =
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
