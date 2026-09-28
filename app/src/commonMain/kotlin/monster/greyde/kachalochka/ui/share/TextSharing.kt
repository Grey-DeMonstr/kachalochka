package monster.greyde.kachalochka.ui.share

/** Android's share sheet, the web's clipboard. */
interface TextSharing {
    /** What the screen should confirm, or null when the platform's own sheet took over. */
    suspend fun share(text: String): String?
}
