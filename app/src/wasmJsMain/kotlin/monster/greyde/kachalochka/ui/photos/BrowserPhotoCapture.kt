package monster.greyde.kachalochka.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

private const val LONG_EDGE = 1600
private const val JPEG_QUALITY = 0.85

/**
 * A file input. With `capture` a phone's browser opens its camera; without it, or on a desktop,
 * the browser offers files. The picture is shrunk on a canvas, which draws it upright.
 */
class BrowserPhotoCapture : PhotoCapture {
    @Composable
    override fun rememberLaunchers(onPhoto: (ByteArray) -> Unit): PhotoLaunchers {
        val deliver by rememberUpdatedState(onPhoto)
        return remember {
            PhotoLaunchers(
                takePhoto = { choosePhoto(camera = true) { deliver(it) } },
                pickPhoto = { choosePhoto(camera = false) { deliver(it) } },
            )
        }
    }
}

private fun choosePhoto(
    camera: Boolean,
    onJpeg: (ByteArray) -> Unit,
) = openImagePicker(camera, LONG_EDGE, JPEG_QUALITY) { jpeg ->
    onJpeg(ByteArray(jpeg.length) { jpeg[it] })
}

@Suppress("UNUSED_PARAMETER")
private fun openImagePicker(
    camera: Boolean,
    longEdge: Int,
    quality: Double,
    onJpeg: (Int8Array) -> Unit,
): Unit =
    js(
        """{
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        if (camera) input.setAttribute('capture', 'environment');
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) return;
            const url = URL.createObjectURL(file);
            const image = new Image();
            image.onload = () => {
                const longest = Math.max(image.naturalWidth, image.naturalHeight);
                const scale = Math.min(1, longEdge / longest);
                const canvas = document.createElement('canvas');
                canvas.width = Math.round(image.naturalWidth * scale);
                canvas.height = Math.round(image.naturalHeight * scale);
                canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
                URL.revokeObjectURL(url);
                canvas.toBlob((blob) => {
                    if (blob) blob.arrayBuffer().then((buffer) => onJpeg(new Int8Array(buffer)));
                }, 'image/jpeg', quality);
            };
            image.onerror = () => URL.revokeObjectURL(url);
            image.src = url;
        };
        input.click();
    }""",
    )
