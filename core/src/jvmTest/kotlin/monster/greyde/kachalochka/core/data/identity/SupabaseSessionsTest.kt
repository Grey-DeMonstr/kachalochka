package monster.greyde.kachalochka.core.data.identity

import io.github.jan.supabase.auth.status.RefreshFailureCause.NetworkError
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val NOW = Instant.fromEpochSeconds(1_700_000_000)

private fun userSession(metadata: Map<String, JsonElement>) =
    UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3_600,
        tokenType = "bearer",
        user =
            UserInfo(
                id = "11111111-1111-4111-8111-111111111111",
                aud = "authenticated",
                email = "ivan.petrov@example.test",
                userMetadata = JsonObject(metadata),
            ),
        expiresAt = NOW + 1.hours,
    )

class SupabaseSessionsTest {
    private val stored =
        AccountSession(
            Account(
                UserId("11111111-1111-4111-8111-111111111111"),
                "ivan.petrov@example.test",
                "Иван Петров",
            ),
            "access",
            "refresh",
            NOW + 1.hours,
        )

    @Test
    fun a_stored_session_survives_the_round_trip_through_supabase() {
        assertEquals(stored, stored.toUserSession(NOW).toAccountSession())
    }

    @Test
    fun the_live_session_expires_when_the_stored_one_does() {
        assertEquals(3_600, stored.toUserSession(NOW).expiresIn)
    }

    @Test
    fun a_session_already_past_its_expiry_asks_for_no_lifetime_at_all() {
        assertEquals(0, stored.toUserSession(NOW + 2.hours).expiresIn)
    }

    @Test
    fun the_name_google_sent_becomes_the_display_name() {
        val metadata = mapOf("full_name" to JsonPrimitive("Иван Петров"))

        assertEquals("Иван Петров", userSession(metadata).toAccountSession().account.displayName)
    }

    @Test
    fun an_account_with_no_name_falls_back_to_the_e_mail_local_part() {
        assertEquals(
            "ivan.petrov",
            userSession(emptyMap()).toAccountSession().account.displayName,
        )
    }

    @Test
    fun a_name_claim_sent_as_null_falls_back_to_the_e_mail_local_part() {
        val metadata = mapOf("full_name" to JsonNull)

        assertEquals("ivan.petrov", userSession(metadata).toAccountSession().account.displayName)
    }

    @Test
    fun a_blank_name_claim_gives_way_to_the_next_one() {
        val metadata =
            mapOf(
                "full_name" to JsonPrimitive("  "),
                "name" to JsonPrimitive("Иван"),
            )

        assertEquals("Иван", userSession(metadata).toAccountSession().account.displayName)
    }

    @Test
    fun an_authenticated_status_carries_the_session_to_write_back() =
        runTest {
            val session = userSession(emptyMap())
            val status = SessionStatus.Authenticated(session, SessionSource.External)

            assertEquals(
                listOf(LiveSessionChange.Renewed(session.toAccountSession())),
                flowOf(status).liveSessionChanges().toList(),
            )
        }

    @Test
    fun a_cleared_session_reads_as_ended() =
        runTest {
            assertEquals(
                listOf(LiveSessionChange.Ended),
                flowOf(SessionStatus.NotAuthenticated(isSignOut = false))
                    .liveSessionChanges()
                    .toList(),
            )
        }

    @Test
    fun initializing_says_nothing() =
        runTest {
            assertEquals(
                emptyList(),
                flowOf(SessionStatus.Initializing).liveSessionChanges().toList(),
            )
        }

    @Test
    fun a_refresh_that_could_not_reach_the_server_says_nothing() =
        runTest {
            val failure = SessionStatus.RefreshFailure(NetworkError(IllegalStateException()))

            assertEquals(emptyList(), flowOf(failure).liveSessionChanges().toList())
        }

    @Test
    fun a_session_without_a_user_says_nothing() =
        runTest {
            val status =
                SessionStatus.Authenticated(
                    userSession(emptyMap()).copy(user = null),
                    SessionSource.External,
                )

            assertEquals(emptyList(), flowOf(status).liveSessionChanges().toList())
        }
}
