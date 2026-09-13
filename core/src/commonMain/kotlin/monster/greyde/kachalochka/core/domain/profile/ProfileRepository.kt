package monster.greyde.kachalochka.core.domain.profile

interface ProfileRepository {
    suspend fun upsert(profile: Profile)

    suspend fun byId(id: ProfileId): Profile?
}
