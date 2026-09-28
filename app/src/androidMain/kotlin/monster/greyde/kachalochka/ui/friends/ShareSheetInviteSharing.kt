package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.ui.share.TextSharing

class ShareSheetInviteSharing(
    private val sharing: TextSharing,
    override val pageAddress: String?,
) : InviteSharing {
    override suspend fun share(invite: Invite): String? = sharing.share(inviteText(invite))
}
