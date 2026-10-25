package monster.greyde.kachalochka.ui.share

/** Android's share sheet, the browser's share sheet or a download. */
interface PhotoSharing {
    suspend fun share(jpeg: ByteArray)
}
