package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.PhotoId

class InMemoryPhotoFiles : PhotoFiles {
    val bytes = mutableMapOf<PhotoId, ByteArray>()

    override fun read(id: PhotoId): ByteArray? = bytes[id]

    override fun write(
        id: PhotoId,
        jpeg: ByteArray,
    ) {
        bytes[id] = jpeg
    }

    override fun delete(id: PhotoId) {
        bytes.remove(id)
    }
}
