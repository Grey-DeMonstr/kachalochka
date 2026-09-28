package monster.greyde.kachalochka.ui.friends

import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asDeferred

/** The web has no share sheet, so an invite goes to the clipboard instead. */
class ClipboardInviteSharing : InviteSharing {
    override val pageAddress: String get() = window.location.href

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun share(invite: Invite): String? =
        try {
            window.navigator.clipboard
                .writeText(invite.link ?: inviteText(invite))
                .asDeferred()
                .await()
            "Ссылка скопирована"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: Throwable) {
            "Код: ${invite.code}"
        }
}
