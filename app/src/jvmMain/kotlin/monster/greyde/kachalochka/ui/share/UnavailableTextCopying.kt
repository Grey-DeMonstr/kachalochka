package monster.greyde.kachalochka.ui.share

/** The JVM target only hosts tests, which bind a recorder instead of this object. */
object UnavailableTextCopying : TextCopying {
    override suspend fun copy(text: String): Boolean = false
}
