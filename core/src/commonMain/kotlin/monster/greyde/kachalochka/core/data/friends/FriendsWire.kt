package monster.greyde.kachalochka.core.data.friends

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.data.profile.avatarPhotoOf
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId

internal const val FRIEND_GROUP_TABLE: String = "friend_group"
internal const val GROUP_MEMBER_TABLE: String = "group_member"

@Serializable
internal data class FriendGroupRow(
    val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("invite_code") val inviteCode: String,
    val deleted: Boolean,
) {
    fun toGroup(memberCount: Int): FriendGroup =
        FriendGroup(GroupId(id), name, UserId(ownerId), inviteCode, memberCount)
}

// Only ever read, so the columns 0020 adds may default for a server that has not got them yet.
@Serializable
internal data class GroupMemberRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val deleted: Boolean,
    @SerialName("avatar_photo") val avatarPhoto: String? = null,
    @SerialName("picture_url") val pictureUrl: String? = null,
) {
    val avatar: Avatar
        get() = Avatar(avatarPhotoOf(avatarPhoto), pictureUrl?.takeIf { it.isNotBlank() })
}

@Serializable
internal data class GroupDeletion(
    val deleted: Boolean,
    @SerialName("updated_at") val updatedAt: String,
)
