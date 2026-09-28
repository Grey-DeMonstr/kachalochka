package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.PhotoQueries
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalPhotoRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val files: PhotoFiles,
    private val dispatcher: CoroutineDispatcher,
) : PhotoRepository {
    private val queries = database.photoQueries

    // The file goes first, so a row never names bytes the device does not have.
    override suspend fun add(
        photo: Photo,
        jpeg: ByteArray,
    ) = withContext(dispatcher) {
        files.write(photo.id, jpeg)
        write(photo)
    }

    override suspend fun upsert(photo: Photo) =
        withContext(dispatcher) {
            write(photo)
            if (photo.deleted) files.delete(photo.id)
        }

    override suspend fun forMachine(machineId: MachineId): List<Photo> =
        withContext(dispatcher) { queries.forMachine(machineId.value, ::photoOf).executeAsList() }

    override suspend fun all(owner: UserId?): List<Photo> =
        withContext(dispatcher) { queries.live(owner?.value, ::photoOf).executeAsList() }

    private fun write(photo: Photo) =
        queries.transaction {
            queries.write(photo)
            if (photo.userId != null) {
                outbox.enqueue(OutboxEntry(PHOTO_TABLE, photo.id.value, photo.updatedAt))
            }
        }
}

internal fun PhotoQueries.write(photo: Photo) =
    upsert(
        photo.id.value,
        photo.userId?.value,
        photo.machineId.value,
        photo.takenAt,
        photo.updatedAt,
        photo.deleted,
    )

internal fun photoOf(
    id: String,
    userId: String?,
    machineId: String,
    takenAt: Instant,
    updatedAt: Instant,
    deleted: Boolean,
) = Photo(
    id = PhotoId(id),
    userId = userId?.let(::UserId),
    machineId = MachineId(machineId),
    takenAt = takenAt,
    updatedAt = updatedAt,
    deleted = deleted,
)
