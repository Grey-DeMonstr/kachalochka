package monster.greyde.kachalochka.core.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import monster.greyde.kachalochka.core.domain.profile.Sex
import kotlin.time.Instant

/** The name the outbox and PostgREST both address profile rows by. */
const val PROFILE_TABLE: String = "profile"

internal fun friendColorsText(map: Map<UserId, Int>): String =
    Json.encodeToString(map.mapKeys { it.key.value })

// Another client's text is outside this one's control, and one unreadable entry must not stop a
// pull or cost the colours that can be read.
internal fun friendColorsOf(text: String): Map<UserId, Int> {
    val entries =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return emptyMap()
    return entries
        .mapNotNull { (key, value) ->
            val user = runCatching { UserId(key) }.getOrNull()
            val color = (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            if (user != null && color != null) user to color else null
        }.toMap()
}

internal fun Sex.wireName(): String =
    when (this) {
        Sex.Male -> "male"
        Sex.Female -> "female"
    }

internal fun sexOf(wire: String?): Sex? = Sex.entries.firstOrNull { it.wireName() == wire }

// Postgres names its columns with underscores and stores the instant as a timestamptz string,
// so the wire shape is its own type and the domain entity stays serialization-neutral.
@Serializable
internal data class ProfileRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("display_name") val displayName: String?,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
    @SerialName("friend_colors") val friendColors: String,
    val sex: String?,
    @SerialName("birth_year") val birthYear: Int?,
    @SerialName("height_cm") val heightCm: Double?,
) {
    fun toProfile(): Profile =
        Profile(
            id = ProfileId(id),
            userId = userId?.let(::UserId),
            displayName = displayName,
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
            friendColors = friendColorsOf(friendColors),
            sex = sexOf(sex),
            birthYear = birthYear,
            heightCm = heightCm,
        )

    companion object {
        fun of(profile: Profile): ProfileRow =
            ProfileRow(
                id = profile.id.value,
                userId = profile.userId?.value,
                displayName = profile.displayName,
                updatedAt = profile.updatedAt.toString(),
                deleted = profile.deleted,
                friendColors = friendColorsText(profile.friendColors),
                sex = profile.sex?.wireName(),
                birthYear = profile.birthYear,
                heightCm = profile.heightCm,
            )
    }
}
