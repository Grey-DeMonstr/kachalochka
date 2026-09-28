package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.PhotoId

/** The web keeps nothing on the device: every photo is read from the server. */
object NoPhotoFiles : PhotoFiles {
    override fun read(id: PhotoId): ByteArray? = null

    override fun write(
        id: PhotoId,
        jpeg: ByteArray,
    ) = Unit

    override fun delete(id: PhotoId) = Unit
}
