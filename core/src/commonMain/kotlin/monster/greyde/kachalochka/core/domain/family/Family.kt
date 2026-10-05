package monster.greyde.kachalochka.core.domain.family

import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId

/** Someone on the other end of a guardian link, named and pictured as group mates see them. */
data class FamilyMember(
    val userId: UserId,
    val displayName: String,
    val avatar: Avatar = Avatar(),
)

/** Whom the account guards and who guards it, each by name. */
data class Family(
    val children: List<FamilyMember>,
    val guardians: List<FamilyMember>,
)

sealed interface Acceptance {
    data class Linked(
        val guardian: UserId,
    ) : Acceptance

    /** No live code matches: mistyped, expired or already taken. */
    data object UnknownCode : Acceptance

    data object OwnCode : Acceptance
}

/** The active account's guardian links, read and changed online. */
interface FamilyRepository {
    suspend fun family(): Family

    /** A code a child enters within 24 hours, once; the account's earlier code stops working. */
    suspend fun offer(): String

    suspend fun accept(code: String): Acceptance

    /** Ends the link of [child] and [guardian]; the account must be one of them. */
    suspend fun end(
        child: UserId,
        guardian: UserId,
    )
}
