package monster.greyde.kachalochka.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardTextCopying(
    private val context: Context,
) : TextCopying {
    override suspend fun copy(text: String): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
        return true
    }
}
