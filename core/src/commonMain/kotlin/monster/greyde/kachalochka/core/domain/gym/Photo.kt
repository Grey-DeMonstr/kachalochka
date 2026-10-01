package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.identity.newUuidV4
import monster.greyde.kachalochka.core.domain.identity.requireUuidV4
import kotlin.jvm.JvmInline
import kotlin.time.Instant

@JvmInline
value class PhotoId(
    val value: String,
) {
    init {
        requireUuidV4(value, "PhotoId")
    }

    companion object {
        fun random(): PhotoId = PhotoId(newUuidV4())
    }
}

/** A photo of a machine; its JPEG bytes travel apart from the row. */
data class Photo(
    val id: PhotoId,
    val userId: UserId?,
    val machineId: MachineId,
    val takenAt: Instant,
    val updatedAt: Instant,
    val deleted: Boolean,
) {
    companion object {
        fun new(
            machineId: MachineId,
            userId: UserId?,
            now: Instant,
        ): Photo = Photo(PhotoId.random(), userId, machineId, now, now, false)
    }
}

val photoOrder: Comparator<Photo> = compareBy<Photo> { it.takenAt }.thenBy { it.id.value }

/**
 * The photo standing for [machine]: the [chosen] one while it is live in the machine's cluster,
 * else its own first, else the first of a machine linked to it.
 */
fun coverPhoto(
    machine: MachineId,
    photos: List<Photo>,
    clusters: MachineClusters,
    chosen: PhotoId? = null,
): Photo? {
    val live = photos.filterNot { it.deleted }
    val cluster = clusters.of(machine)
    return live.firstOrNull { it.id == chosen && it.machineId in cluster }
        ?: live.filter { it.machineId == machine }.minWithOrNull(photoOrder)
        ?: live.filter { it.machineId in cluster }.minWithOrNull(photoOrder)
}

interface PhotoRepository {
    /** Stores [jpeg] as the bytes of [photo], then the row. */
    suspend fun add(
        photo: Photo,
        jpeg: ByteArray,
    )

    suspend fun upsert(photo: Photo)

    /** The machine's live photos in [photoOrder]. */
    suspend fun forMachine(machineId: MachineId): List<Photo>

    /** The owner's live photos of every machine. */
    suspend fun all(owner: UserId?): List<Photo>
}
