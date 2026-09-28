package monster.greyde.kachalochka.core.data.profile

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.ProfileQueries
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class LocalProfileRepository(
    database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val dispatcher: CoroutineDispatcher,
) : ProfileRepository {
    private val queries = database.profileQueries

    // The row and its outbox entry are one write: an entry without its row would push stale data,
    // and a row without its entry would never reach the server at all.
    override suspend fun upsert(profile: Profile) =
        withContext(dispatcher) {
            queries.transaction {
                queries.write(profile)
                // An owner is what a row-level-security policy matches on, so an unowned row
                // waits for the login that stamps it (technical spec §4.3).
                if (profile.userId != null) {
                    outbox.enqueue(OutboxEntry(PROFILE_TABLE, profile.id.value, profile.updatedAt))
                }
            }
        }

    override suspend fun byId(id: ProfileId): Profile? =
        withContext(dispatcher) { queries.byId(id.value, ::profileOf).executeAsOneOrNull() }

    override suspend fun forOwner(owner: UserId?): Profile? =
        withContext(dispatcher) {
            queries.forOwner(owner?.value, ::profileOf).executeAsOneOrNull()
        }
}

internal fun ProfileQueries.write(profile: Profile) =
    upsert(
        profile.id.value,
        profile.userId?.value,
        profile.displayName,
        profile.updatedAt,
        profile.deleted,
        friendColorsText(profile.friendColors),
        profile.sex?.wireName(),
        profile.birthDate?.iso,
        profile.heightCm,
        profile.weightUnit.wireName(),
    )

internal fun profileOf(
    id: String,
    userId: String?,
    displayName: String?,
    updatedAt: Instant,
    deleted: Boolean,
    friendColors: String,
    sex: String?,
    birthDate: String?,
    heightCm: Double?,
    weightUnit: String,
) = Profile(
    id = ProfileId(id),
    userId = userId?.let(::UserId),
    displayName = displayName,
    updatedAt = updatedAt,
    deleted = deleted,
    friendColors = friendColorsOf(friendColors),
    sex = sexOf(sex),
    birthDate = birthDateOf(birthDate),
    heightCm = heightCm,
    weightUnit = weightUnitOf(weightUnit),
)
