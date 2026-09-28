package monster.greyde.kachalochka.ui.friends

/** The JVM target only hosts tests, which bind a recorder instead of this object. */
object UnavailableInviteSharing : InviteSharing {
    override val pageAddress: String? = null

    override suspend fun share(invite: Invite): String? = null
}
