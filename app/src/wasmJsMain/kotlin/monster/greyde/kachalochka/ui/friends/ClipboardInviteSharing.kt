package monster.greyde.kachalochka.ui.friends

import kotlinx.browser.window
import monster.greyde.kachalochka.ui.share.copyToClipboard

/** The web has no share sheet, so an invite goes to the clipboard instead. */
class ClipboardInviteSharing : InviteSharing {
    override val pageAddress: String get() = window.location.href

    override suspend fun share(invite: Invite): String? =
        if (copyToClipboard(invite.link ?: inviteText(invite))) {
            "Ссылка скопирована"
        } else {
            "Код: ${invite.code}"
        }
}
