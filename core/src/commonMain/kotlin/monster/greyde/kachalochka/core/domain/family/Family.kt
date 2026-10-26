package monster.greyde.kachalochka.core.domain.family

import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId

/** Someone on the other end of a guardian or a coaching link, named as group mates see them. */
data class FamilyMember(
    val userId: UserId,
    val displayName: String,
    val avatar: Avatar = Avatar(),
)

/** Whom the account guards and coaches, and who guards and coaches it, each by name. */
data class Family(
    val children: List<FamilyMember>,
    val guardians: List<FamilyMember>,
    val trainees: List<FamilyMember> = emptyList(),
    val trainers: List<FamilyMember> = emptyList(),
)

sealed interface Acceptance {
    /** Linked to [other], whose code it was. */
    data class Linked(
        val other: UserId,
    ) : Acceptance

    /** No live code matches: mistyped, expired or already taken. */
    data object UnknownCode : Acceptance

    data object OwnCode : Acceptance
}

/** The active account's guardian and coaching links, read and changed online. */
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

    /** A code a trainee enters as [offer]'s is entered, with its own earlier code retired. */
    suspend fun offerCoaching(): String

    suspend fun acceptTrainer(code: String): Acceptance

    /** Ends the link of [trainee] and [trainer]; the account must be one of them. */
    suspend fun endCoaching(
        trainee: UserId,
        trainer: UserId,
    )
}
