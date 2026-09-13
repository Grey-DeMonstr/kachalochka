package monster.greyde.kachalochka.core.data.profile

import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalProfileRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
) : ProfileRepository {
    private val queries = database.profileQueries

    // The row and its outbox entry are one write: an entry without its row would push stale data,
    // and a row without its entry would never reach the server at all.
    override suspend fun upsert(profile: Profile) {
        queries.transaction {
            queries.upsert(
                profile.id,
                profile.userId,
                profile.displayName,
                profile.updatedAt.epochSeconds,
                if (profile.deleted) 1L else 0L,
            )
            // An owner is what a row-level-security policy matches on, so an unowned row waits
            // for the login that stamps it (technical spec §4.3).
            if (profile.userId != null) {
                outbox.enqueue(OutboxEntry(PROFILE_TABLE, profile.id, profile.updatedAt))
            }
        }
    }

    override suspend fun byId(id: String): Profile? =
        queries
            .byId(id) { rowId, userId, displayName, updatedAt, deleted ->
                Profile(
                    id = rowId,
                    userId = userId,
                    displayName = displayName,
                    updatedAt = Instant.fromEpochSeconds(updatedAt),
                    deleted = deleted != 0L,
                )
            }.executeAsOneOrNull()
}
