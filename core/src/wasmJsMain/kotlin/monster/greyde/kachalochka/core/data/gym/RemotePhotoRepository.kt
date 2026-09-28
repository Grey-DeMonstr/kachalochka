package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.photoOrder
import monster.greyde.kachalochka.core.domain.identity.UserId

class RemotePhotoRepository(
    private val client: SupabaseClient,
) : PhotoRepository {
    private val bucket get() = client.storage.from(PHOTO_BUCKET)

    override suspend fun add(
        photo: Photo,
        jpeg: ByteArray,
    ) {
        bucket.upload(checkNotNull(photo.storagePath()), jpeg) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
        upsert(photo)
    }

    override suspend fun upsert(photo: Photo) {
        if (photo.deleted) photo.storagePath()?.let { bucket.delete(it) }
        client.postgrest.from(PHOTO_TABLE).upsert(PhotoRow.of(photo))
    }

    override suspend fun forMachine(machineId: MachineId): List<Photo> =
        client.postgrest
            .from(PHOTO_TABLE)
            .select {
                filter {
                    eq("machine_id", machineId.value)
                    eq("deleted", false)
                }
            }.decodeList<PhotoRow>()
            .map { it.toPhoto() }
            .sortedWith(photoOrder)

    override suspend fun all(owner: UserId?): List<Photo> =
        client.postgrest
            .from(PHOTO_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    owned(owner)
                }
            }.decodeList<PhotoRow>()
            .map { it.toPhoto() }
}
