package monster.greyde.kachalochka.core.data.sync

import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

class SyncWatermarks(
    database: KachalochkaDatabase,
) {
    private val queries = database.syncQueries

    fun lastPullAt(owner: UserId): Instant? =
        queries.lastPullAt(owner.value).executeAsOneOrNull()?.lastPullAt

    fun advance(
        owner: UserId,
        to: Instant,
    ) {
        queries.setLastPullAt(owner.value, to)
    }
}
