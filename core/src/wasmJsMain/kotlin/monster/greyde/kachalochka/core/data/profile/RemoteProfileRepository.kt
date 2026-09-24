package monster.greyde.kachalochka.core.data.profile

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository

class RemoteProfileRepository(
    private val client: SupabaseClient,
) : ProfileRepository {
    override suspend fun upsert(profile: Profile) {
        client.postgrest.from(PROFILE_TABLE).upsert(ProfileRow.of(profile))
    }

    override suspend fun byId(id: ProfileId): Profile? =
        client.postgrest
            .from(PROFILE_TABLE)
            .select { filter { eq("id", id.value) } }
            .decodeSingleOrNull<ProfileRow>()
            ?.toProfile()
}
