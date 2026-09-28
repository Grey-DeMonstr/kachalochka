package monster.greyde.kachalochka.core.domain.profile

import monster.greyde.kachalochka.core.domain.identity.UserId

interface ProfileRepository {
    suspend fun upsert(profile: Profile)

    suspend fun byId(id: ProfileId): Profile?

    /** The owner's live profile with the newest `updated_at`. */
    suspend fun forOwner(owner: UserId?): Profile?
}
