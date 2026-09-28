package monster.greyde.kachalochka.ui.photos

import androidx.compose.runtime.Composable

class PhotoLaunchers(
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
)

/** Brings a new photo in from the platform's camera or gallery, as a JPEG ready to store. */
interface PhotoCapture {
    /** Null where the platform offers neither. */
    @Composable
    fun rememberLaunchers(onPhoto: (ByteArray) -> Unit): PhotoLaunchers?
}

object NoPhotoCapture : PhotoCapture {
    @Composable
    override fun rememberLaunchers(onPhoto: (ByteArray) -> Unit): PhotoLaunchers? = null
}
