package monster.greyde.kachalochka.ui.share

import android.content.Context
import android.content.Intent

/** Hands the text to whatever app the user picks; the share sheet needs no confirmation back. */
class ShareSheetTextSharing(
    private val context: Context,
) : TextSharing {
    override suspend fun share(text: String): String? {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(
            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return null
    }
}
