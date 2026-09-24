package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

private fun refreshTo(renewed: AccountSession?) =
    object : SessionRefresh {
        override suspend fun refresh(session: AccountSession) = renewed
    }

private val mustNotRefresh =
    object : SessionRefresh {
        override suspend fun refresh(session: AccountSession): AccountSession? =
            error("a usable token must not be refreshed")
    }

class AccountTokensTest {
    private val ivan = accountSession("11111111-1111-4111-8111-111111111111", "Ivan")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")
    private val store = PersistedAccountStore(InMemoryAccountStorage())
    private val neverLive = LiveTokens { null }

    @Test
    fun a_token_well_before_expiry_is_handed_back_unchanged() =
        runTest {
            store.add(ivan)
            val tokens =
                AccountTokens(store, neverLive, mustNotRefresh, clockAt(FIXTURE_EXPIRY - 1.hours))

            assertEquals(ivan.accessToken, tokens.tokenFor(ivan.account.userId))
        }

    @Test
    fun a_token_near_expiry_is_refreshed_and_stored() =
        runTest {
            store.add(ivan)
            val renewed = ivan.copy(accessToken = "fresh")
            val tokens =
                AccountTokens(store, neverLive, refreshTo(renewed), clockAt(FIXTURE_EXPIRY))

            assertEquals("fresh", tokens.tokenFor(ivan.account.userId))
            assertEquals("fresh", store.sessionOf(ivan.account.userId)?.accessToken)
        }

    @Test
    fun the_live_account_s_token_comes_from_the_live_session() =
        runTest {
            store.add(ivan)
            val tokens =
                AccountTokens(
                    store,
                    LiveTokens { "live" },
                    mustNotRefresh,
                    clockAt(FIXTURE_EXPIRY),
                )

            assertEquals("live", tokens.tokenFor(ivan.account.userId))
        }

    @Test
    fun a_refused_refresh_leaves_no_token() =
        runTest {
            store.add(ivan)
            val tokens =
                AccountTokens(store, neverLive, refreshTo(null), clockAt(FIXTURE_EXPIRY))

            assertNull(tokens.tokenFor(ivan.account.userId))
            assertEquals(ivan, store.sessionOf(ivan.account.userId))
        }

    @Test
    fun an_unknown_account_has_no_token() =
        runTest {
            val tokens =
                AccountTokens(store, neverLive, mustNotRefresh, clockAt(FIXTURE_EXPIRY))

            assertNull(tokens.tokenFor(misha))
        }
}
