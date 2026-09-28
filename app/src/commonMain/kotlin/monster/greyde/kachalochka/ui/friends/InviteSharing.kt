package monster.greyde.kachalochka.ui.friends

/** Android's share sheet, the web's clipboard. */
interface InviteSharing {
    /** Where invite links start; null in an Android build without `WEB_APP_URL`. */
    val pageAddress: String?

    /** What the screen should confirm, or null when the platform's own sheet took over. */
    suspend fun share(invite: Invite): String?
}
