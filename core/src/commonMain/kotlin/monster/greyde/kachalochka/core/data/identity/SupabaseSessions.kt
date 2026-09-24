package monster.greyde.kachalochka.core.data.identity

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Instant

private const val TOKEN_TYPE = "bearer"
private const val FULL_NAME_CLAIM = "full_name"

// Supabase copies the Google claim verbatim, and which of the two it lands under depends on what
// the provider sent.
private val NAME_CLAIMS = listOf(FULL_NAME_CLAIM, "name")

/** The client is resolved on the first session change: a build without credentials still runs. */
class SupabaseSessions(
    private val client: Lazy<SupabaseClient>,
    private val clock: Clock = Clock.System,
) : SessionActivation,
    SessionRefresh {
    override suspend fun activate(session: AccountSession) {
        client.value.auth.importSession(session.toUserSession(clock.now()))
    }

    override suspend fun clear() {
        client.value.auth.clearSession()
    }

    override suspend fun refresh(session: AccountSession): AccountSession? =
        try {
            client.value.auth
                .refreshSession(session.refreshToken)
                .toAccountSession()
        } catch (refused: RestException) {
            if (refused.statusCode in 400..499) null else throw refused
        }
}

fun Flow<SessionStatus>.liveSessionChanges(): Flow<LiveSessionChange> =
    mapNotNull { status ->
        when (status) {
            is SessionStatus.Authenticated ->
                runCatching { status.session.toAccountSession() }
                    .getOrNull()
                    ?.let(LiveSessionChange::Renewed)
            is SessionStatus.NotAuthenticated -> LiveSessionChange.Ended
            SessionStatus.Initializing -> LiveSessionChange.Reloading
            else -> null
        }
    }

fun AccountSession.toUserSession(now: Instant): UserSession =
    UserSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = (expiresAt - now).inWholeSeconds.coerceAtLeast(0),
        tokenType = TOKEN_TYPE,
        user =
            UserInfo(
                id = account.userId.value,
                aud = "authenticated",
                email = account.email,
                userMetadata =
                    JsonObject(mapOf(FULL_NAME_CLAIM to JsonPrimitive(account.displayName))),
            ),
        expiresAt = expiresAt,
    )

fun UserSession.toAccountSession(): AccountSession {
    val user = user ?: error("Supabase returned a session without a user")
    val email = user.email ?: error("Supabase returned a user without an e-mail")
    return AccountSession(
        // Postgres spells uuids in lower case; anything else would be rejected by UserId.
        Account(UserId(user.id.lowercase()), email, user.displayName(email)),
        accessToken,
        refreshToken,
        expiresAt,
    )
}

private fun UserInfo.displayName(email: String): String =
    NAME_CLAIMS.firstNotNullOfOrNull { nameClaim(it) }
        ?: email.substringBefore('@').ifBlank { email }

// JsonNull is a JsonPrimitive whose content is the word "null", so a claim the provider sent
// empty has to be turned away before it reaches anybody's screen.
private fun UserInfo.nameClaim(claim: String): String? =
    (userMetadata?.get(claim) as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.takeIf { it.isNotBlank() }
