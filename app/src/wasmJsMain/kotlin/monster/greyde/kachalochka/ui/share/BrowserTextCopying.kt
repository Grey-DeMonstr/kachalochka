package monster.greyde.kachalochka.ui.share

class BrowserTextCopying : TextCopying {
    override suspend fun copy(text: String): Boolean = copyToClipboard(text)
}
