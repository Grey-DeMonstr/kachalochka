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
