package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.CompletableDeferred
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

    /** While set, a refresh started now waits for it, keeping it in flight while a test needs. */
    var gate: CompletableDeferred<Unit>? = null

    override fun liveSessionOf(owner: UserId): AccountSession? =
        session?.takeIf { it.account.userId == owner }

    override suspend fun refreshLive(owner: UserId): String? {
        refreshes++
        gate?.await()
        return renewed
    }
}

internal class RecordingWatermarkReset : WatermarkReset {
    val forgotten = mutableListOf<UserId>()

    override suspend fun forget(owner: UserId) {
        forgotten += owner
    }
}

internal val SASHA_ID = UserId("66666666-6666-4666-8666-666666666666")

/** A child [guardian] acts for, as `FamilyFollower` lists one. */
internal fun managedChild(
    guardian: AccountSession,
    id: UserId = SASHA_ID,
    name: String = "Sasha",
) = Account(id, "", name, kind = AccountKind.Managed(guardian.account.userId))
