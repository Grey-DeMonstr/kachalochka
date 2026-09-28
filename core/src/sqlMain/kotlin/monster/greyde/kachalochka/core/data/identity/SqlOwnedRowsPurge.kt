package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.gym.PhotoFiles
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.UserId

/** Outbox entries of the removed rows stay; the next pass drops an entry whose row is gone. */
class SqlOwnedRowsPurge(
    private val database: KachalochkaDatabase,
    private val files: PhotoFiles,
    private val dispatcher: CoroutineDispatcher,
) : OwnedRowsPurge {
    override suspend fun purge(owner: UserId) =
        withContext(dispatcher) {
            val id = owner.value
            val photos = database.photoQueries.ownedIds(id).executeAsList()
            database.transaction {
                database.workoutSetQueries.purgeOwner(id)
                database.visitQueries.purgeOwner(id)
                database.machineLinkQueries.purgeOwner(id)
                database.photoQueries.purgeOwner(id)
                database.machineQueries.purgeOwner(id)
                database.measurementQueries.purgeOwner(id)
                database.measureQueries.purgeOwner(id)
                database.profileQueries.purgeOwner(id)
                database.syncQueries.forgetWatermark(id)
            }
            photos.forEach { files.delete(PhotoId(it)) }
        }
}
