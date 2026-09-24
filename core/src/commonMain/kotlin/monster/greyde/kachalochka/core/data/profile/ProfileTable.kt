package monster.greyde.kachalochka.core.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import kotlin.time.Instant

/** The name the outbox and PostgREST both address profile rows by. */
const val PROFILE_TABLE: String = "profile"

// Postgres names its columns with underscores and stores the instant as a timestamptz string,
// so the wire shape is its own type and the domain entity stays serialization-neutral.
@Serializable
internal data class ProfileRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("display_name") val displayName: String?,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toProfile(): Profile =
        Profile(
            id = ProfileId(id),
            userId = userId?.let(::UserId),
            displayName = displayName,
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(profile: Profile): ProfileRow =
            ProfileRow(
                id = profile.id.value,
                userId = profile.userId?.value,
                displayName = profile.displayName,
                updatedAt = profile.updatedAt.toString(),
                deleted = profile.deleted,
            )
    }
}
