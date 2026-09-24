package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Instant

internal val FIXTURE_EXPIRY: Instant = Instant.fromEpochSeconds(1_700_000_000)

internal fun accountSession(
    id: String,
    name: String,
    expiresAt: Instant = FIXTURE_EXPIRY,
) = AccountSession(
    Account(UserId(id), "$name@example.test", name),
    accessToken = "access-$name",
    refreshToken = "refresh-$name",
    expiresAt = expiresAt,
)

internal fun clockAt(at: Instant): Clock =
    object : Clock {
        override fun now(): Instant = at
    }

/** Stands in for a UI client with nobody live, or a given session live. */
internal class FakeLiveTokens(
    private val session: AccountSession? = null,
    private val renewed: String? = null,
) : LiveTokens {
    var refreshes = 0

    override fun liveSessionOf(owner: UserId): AccountSession? =
        session?.takeIf { it.account.userId == owner }

    override suspend fun refreshLive(owner: UserId): String? {
        refreshes++
        return renewed
    }
}
