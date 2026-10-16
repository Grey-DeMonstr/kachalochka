package monster.greyde.kachalochka.ui.share

/** The platform's clipboard. */
interface TextCopying {
    /** False when the platform refused the text. */
    suspend fun copy(text: String): Boolean
}
