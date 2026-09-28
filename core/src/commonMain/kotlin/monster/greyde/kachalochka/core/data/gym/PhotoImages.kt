package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.domain.gym.Photo

/** The JPEG bytes of any photo the viewer may see, own or a group mate's. */
fun interface PhotoImages {
    /** Null when neither the device nor the server can have them; throws when offline. */
    suspend fun bytes(photo: Photo): ByteArray?
}

/**
 * The device's copy first, else the server's, read as the active account through [client]. A
 * download [keeps] names is stored, so an own photo pulled from another device is fetched once.
 */
class StoragePhotoImages(
    private val client: Lazy<SupabaseClient>,
    private val files: PhotoFiles,
    private val keeps: (Photo) -> Boolean,
    private val dispatcher: CoroutineDispatcher,
) : PhotoImages {
    override suspend fun bytes(photo: Photo): ByteArray? {
        withContext(dispatcher) { files.read(photo.id) }?.let { return it }
        val path = photo.storagePath() ?: return null
        val downloaded =
            client.value.storage
                .from(PHOTO_BUCKET)
                .downloadAuthenticated(path)
        if (keeps(photo)) withContext(dispatcher) { files.write(photo.id, downloaded) }
        return downloaded
    }
}
