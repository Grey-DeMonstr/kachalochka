package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.PhotoId
import java.io.File

/** One JPEG per photo in the app's private [directory]. */
class DirectoryPhotoFiles(
    private val directory: File,
) : PhotoFiles {
    private fun fileOf(id: PhotoId) = File(directory, "${id.value}.jpg")

    override fun read(id: PhotoId): ByteArray? = fileOf(id).takeIf { it.isFile }?.readBytes()

    // Written aside and renamed, so a crash never leaves half a photo under its name.
    override fun write(
        id: PhotoId,
        jpeg: ByteArray,
    ) {
        directory.mkdirs()
        val partial = File(directory, "${id.value}.part")
        partial.writeBytes(jpeg)
        check(partial.renameTo(fileOf(id))) { "Could not store photo ${id.value}" }
    }

    override fun delete(id: PhotoId) {
        fileOf(id).delete()
    }
}
