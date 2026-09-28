package monster.greyde.kachalochka.ui.friends

import android.content.Context
import android.content.Intent

/** Hands the invite to whatever app the user picks; the share sheet needs no confirmation back. */
class ShareSheetInviteSharing(
    private val context: Context,
    override val pageAddress: String?,
) : InviteSharing {
    override suspend fun share(invite: Invite): String? {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, inviteText(invite))
        context.startActivity(
            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return null
    }
}
