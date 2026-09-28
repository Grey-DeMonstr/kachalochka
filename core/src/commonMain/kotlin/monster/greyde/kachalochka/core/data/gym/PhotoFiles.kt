package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.PhotoId

/** The JPEG bytes of the photos kept on this device. Blocking: callers pick the dispatcher. */
interface PhotoFiles {
    fun read(id: PhotoId): ByteArray?

    fun write(
        id: PhotoId,
        jpeg: ByteArray,
    )

    fun delete(id: PhotoId)
}
