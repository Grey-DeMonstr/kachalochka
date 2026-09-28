package monster.greyde.kachalochka.ui.share

import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asDeferred

/** The web has no share sheet, so the text goes to the clipboard instead. */
class ClipboardTextSharing : TextSharing {
    override suspend fun share(text: String): String =
        if (copyToClipboard(text)) "Скопировано" else "Не удалось скопировать"
}

/** False when the browser refuses, as it may without a recent tap or a secure page. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun copyToClipboard(text: String): Boolean =
    try {
        window.navigator.clipboard
            .writeText(text)
            .asDeferred()
            .await()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (refused: Throwable) {
        false
    }
