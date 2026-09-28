package monster.greyde.kachalochka.ui.share

/** The JVM target only hosts tests, which bind a recorder instead of this object. */
object UnavailableTextSharing : TextSharing {
    override suspend fun share(text: String): String? = null
}
