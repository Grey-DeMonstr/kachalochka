package monster.greyde.kachalochka.ui.friends

import kotlinx.browser.window
import monster.greyde.kachalochka.ui.share.copyToClipboard
import monster.greyde.kachalochka.ui.strings.AppStrings

/** The web has no share sheet, so an invite goes to the clipboard instead. */
class ClipboardInviteSharing : InviteSharing {
    override val pageAddress: String get() = window.location.href

    override suspend fun share(invite: Invite): String? =
        if (copyToClipboard(invite.link ?: inviteText(invite))) {
            AppStrings.current.linkCopied
        } else {
            AppStrings.current.codeLine(invite.code)
        }
}
