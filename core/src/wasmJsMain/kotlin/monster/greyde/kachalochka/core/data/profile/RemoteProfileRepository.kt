package monster.greyde.kachalochka.core.data.profile

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import kotlin.time.Instant

class RemoteProfileRepository(
    private val client: SupabaseClient,
) : ProfileRepository {
    override suspend fun upsert(profile: Profile) {
        client.postgrest.from(PROFILE_TABLE).upsert(ProfileRow.of(profile))
    }

    override suspend fun byId(id: String): Profile? =
        client.postgrest
            .from(PROFILE_TABLE)
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<ProfileRow>()
            ?.toProfile()
}

// Postgres names its columns with underscores and stores the instant as a timestamptz string,
// so the wire shape is its own type and the domain entity stays serialization-neutral.
@Serializable
private data class ProfileRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("display_name") val displayName: String?,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toProfile(): Profile =
        Profile(
            id = id,
            userId = userId,
            displayName = displayName,
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(profile: Profile): ProfileRow =
            ProfileRow(
                id = profile.id,
                userId = profile.userId,
                displayName = profile.displayName,
                updatedAt = profile.updatedAt.toString(),
                deleted = profile.deleted,
            )
    }
}
