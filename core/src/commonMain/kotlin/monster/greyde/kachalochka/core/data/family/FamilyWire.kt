package monster.greyde.kachalochka.core.data.family

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.data.gym.photoIdOf
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId

internal const val CHILD_RELATION = "child"

/** One row of `my_family`, migration 0023. */
@Serializable
internal data class FamilyRow(
    @SerialName("user_id") val userId: String,
    val relation: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_photo") val avatarPhoto: String? = null,
    @SerialName("picture_url") val pictureUrl: String? = null,
) {
    fun toMember(): FamilyMember =
        FamilyMember(
            UserId(userId.lowercase()),
            displayName,
            Avatar(photoIdOf(avatarPhoto), pictureUrl?.takeIf { it.isNotBlank() }),
        )
}
