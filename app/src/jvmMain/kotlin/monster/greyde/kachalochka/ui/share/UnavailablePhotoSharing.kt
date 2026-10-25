package monster.greyde.kachalochka.ui.share

/** The JVM target only hosts tests, which bind a recorder instead of this object. */
object UnavailablePhotoSharing : PhotoSharing {
    override suspend fun share(jpeg: ByteArray) = Unit
}
