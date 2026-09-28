package monster.greyde.kachalochka.core.data.gym

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

const val PHOTO_TABLE: String = "photo"
const val PHOTO_BUCKET: String = "photos"

/** Where the photo's bytes live in [PHOTO_BUCKET]: the owner's folder, so policies match it. */
fun Photo.storagePath(): String? = userId?.let { "${it.value}/${id.value}" }

@Serializable
internal data class PhotoRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("machine_id") val machineId: String,
    @SerialName("taken_at") val takenAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toPhoto(): Photo =
        Photo(
            id = PhotoId(id),
            userId = userId?.let(::UserId),
            machineId = MachineId(machineId),
            takenAt = Instant.parse(takenAt),
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(photo: Photo): PhotoRow =
            PhotoRow(
                id = photo.id.value,
                userId = photo.userId?.value,
                machineId = photo.machineId.value,
                takenAt = photo.takenAt.toString(),
                updatedAt = photo.updatedAt.toString(),
                deleted = photo.deleted,
            )
    }
}
