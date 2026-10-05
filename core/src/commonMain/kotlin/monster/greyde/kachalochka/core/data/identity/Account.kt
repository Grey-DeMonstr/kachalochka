package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

data class Account(
    val userId: UserId,
    /** Empty for a managed account, which signs in nowhere. */
    val email: String,
    val displayName: String,
    /** The Google picture's address, when Google sent one. */
    val pictureUrl: String? = null,
    val kind: AccountKind = AccountKind.Google,
) {
    /** The account whose session acts for this one; null for a Google account. */
    val guardianId: UserId? get() = (kind as? AccountKind.Managed)?.guardianId

    val isManaged: Boolean get() = kind is AccountKind.Managed
}

sealed interface AccountKind {
    data object Google : AccountKind

    /** A child linked to [guardianId] (technical spec §4.3); [avatarPhoto] is its own choice. */
    data class Managed(
        val guardianId: UserId,
        val avatarPhoto: PhotoId? = null,
    ) : AccountKind
}

data class AccountSession(
    val account: Account,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
)
