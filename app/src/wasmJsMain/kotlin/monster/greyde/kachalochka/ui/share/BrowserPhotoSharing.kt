package monster.greyde.kachalochka.ui.share

import org.khronos.webgl.Int8Array
import org.khronos.webgl.set

/** The browser's share sheet where it takes files, as phones do; a download elsewhere. */
class BrowserPhotoSharing : PhotoSharing {
    override suspend fun share(jpeg: ByteArray) {
        val bytes = Int8Array(jpeg.size)
        jpeg.forEachIndexed { index, byte -> bytes[index] = byte }
        shareOrDownload(bytes, "photo.jpg")
    }
}

// A share the user cancels is an AbortError; any other refusal falls back to the download.
@Suppress("UNUSED_PARAMETER")
private fun shareOrDownload(
    bytes: Int8Array,
    name: String,
): Unit =
    js(
        """{
        const file = new File([bytes], name, { type: 'image/jpeg' });
        const download = () => {
            const url = URL.createObjectURL(file);
            const link = document.createElement('a');
            link.href = url;
            link.download = name;
            link.click();
            setTimeout(() => URL.revokeObjectURL(url), 0);
        };
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
            navigator.share({ files: [file] }).catch((error) => {
                if (error.name !== 'AbortError') download();
            });
        } else {
            download();
        }
    }""",
    )
