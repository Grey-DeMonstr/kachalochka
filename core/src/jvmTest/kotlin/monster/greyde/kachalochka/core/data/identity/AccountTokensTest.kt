package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.identity.UserId
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

private fun refreshTo(renewed: AccountSession?) =
    object : SessionRefresh {
        override suspend fun refresh(session: AccountSession) = renewed
    }

/** Counts refreshes and holds each one until [gate] completes, so callers can overlap it. */
private class GatedRefresh(
    private val renewed: () -> AccountSession?,
) : SessionRefresh {
    val gate = CompletableDeferred<Unit>()
    var calls = 0

    override suspend fun refresh(session: AccountSession): AccountSession? {
        calls++
        gate.await()
        return renewed()
    }
}

private class CountingStore(
    private val store: AccountStore,
) : AccountStore by store {
    var replaces = 0

    override suspend fun replaceSession(session: AccountSession) {
        replaces++
        store.replaceSession(session)
    }
}

private val mustNotRefresh =
    object : SessionRefresh {
        override suspend fun refresh(session: AccountSession): AccountSession? =
            error("a usable token must not be refreshed")
    }

@OptIn(ExperimentalCoroutinesApi::class)
class AccountTokensTest {
    private val ivan = accountSession("11111111-1111-4111-8111-111111111111", "Ivan")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")
    private val store = PersistedAccountStore(InMemoryAccountStorage())
    private val neverLive = FakeLiveTokens()

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
            val live = FakeLiveTokens(ivan.copy(accessToken = "live"))
            val tokens =
                AccountTokens(store, live, mustNotRefresh, clockAt(FIXTURE_EXPIRY - 1.hours))

            assertEquals("live", tokens.tokenFor(ivan.account.userId))
            assertEquals(0, live.refreshes)
        }

    @Test
    fun a_live_token_near_expiry_is_refreshed_by_the_ui_client() =
        runTest {
            store.add(ivan)
            val live = FakeLiveTokens(ivan, renewed = "renewed")
            val tokens = AccountTokens(store, live, mustNotRefresh, clockAt(FIXTURE_EXPIRY))

            assertEquals("renewed", tokens.tokenFor(ivan.account.userId))
        }

    @Test
    fun a_live_account_whose_refresh_is_refused_has_no_token() =
        runTest {
            store.add(ivan)
            val live = FakeLiveTokens(ivan, renewed = null)
            val tokens = AccountTokens(store, live, mustNotRefresh, clockAt(FIXTURE_EXPIRY))

            assertNull(tokens.tokenFor(ivan.account.userId))
            assertEquals(1, live.refreshes)
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
    fun concurrent_callers_share_one_refresh_of_an_expired_session() =
        runTest {
            store.add(ivan)
            val counted = CountingStore(store)
            val renewed = ivan.copy(accessToken = "fresh", expiresAt = FIXTURE_EXPIRY + 1.hours)
            val refresh = GatedRefresh { renewed }
            val tokens = AccountTokens(counted, neverLive, refresh, clockAt(FIXTURE_EXPIRY))

            val first = async { tokens.tokenFor(ivan.account.userId) }
            val second = async { tokens.tokenFor(ivan.account.userId) }
            runCurrent()
            refresh.gate.complete(Unit)

            assertEquals(listOf("fresh", "fresh"), listOf(first.await(), second.await()))
            assertEquals(1, refresh.calls)
            assertEquals(1, counted.replaces)
            assertEquals("fresh", tokens.tokenFor(ivan.account.userId))
            assertEquals(1, refresh.calls)
        }

    @Test
    fun a_shared_refresh_that_is_refused_leaves_every_caller_without_a_token() =
        runTest {
            store.add(ivan)
            val refresh = GatedRefresh { null }
            val tokens = AccountTokens(store, neverLive, refresh, clockAt(FIXTURE_EXPIRY))

            val first = async { tokens.tokenFor(ivan.account.userId) }
            val second = async { tokens.tokenFor(ivan.account.userId) }
            runCurrent()
            refresh.gate.complete(Unit)

            assertNull(first.await())
            assertNull(second.await())
            assertEquals(1, refresh.calls)
        }

    @Test
    fun a_shared_refresh_that_fails_fails_every_caller() =
        runTest {
            store.add(ivan)
            val refresh = GatedRefresh { throw IOException("no connection") }
            val tokens = AccountTokens(store, neverLive, refresh, clockAt(FIXTURE_EXPIRY))

            val first = async { runCatching { tokens.tokenFor(ivan.account.userId) } }
            val second = async { runCatching { tokens.tokenFor(ivan.account.userId) } }
            runCurrent()
            refresh.gate.complete(Unit)

            assertIs<IOException>(first.await().exceptionOrNull())
            assertIs<IOException>(second.await().exceptionOrNull())
            assertEquals(1, refresh.calls)
        }

    @Test
    fun concurrent_callers_share_one_refresh_of_the_live_session() =
        runTest {
            store.add(ivan)
            val live = FakeLiveTokens(ivan, renewed = "renewed")
            val gate = CompletableDeferred<Unit>().also { live.gate = it }
            val tokens = AccountTokens(store, live, mustNotRefresh, clockAt(FIXTURE_EXPIRY))

            val first = async { tokens.tokenFor(ivan.account.userId) }
            val second = async { tokens.tokenFor(ivan.account.userId) }
            runCurrent()
            gate.complete(Unit)

            assertEquals(listOf("renewed", "renewed"), listOf(first.await(), second.await()))
            assertEquals(1, live.refreshes)
        }

    @Test
    fun a_caller_waiting_on_a_cancelled_refresh_refreshes_itself() =
        runTest {
            store.add(ivan)
            val renewed = ivan.copy(accessToken = "fresh", expiresAt = FIXTURE_EXPIRY + 1.hours)
            val refresh = GatedRefresh { renewed }
            val tokens = AccountTokens(store, neverLive, refresh, clockAt(FIXTURE_EXPIRY))

            val first = async { tokens.tokenFor(ivan.account.userId) }
            runCurrent()
            val second = async { tokens.tokenFor(ivan.account.userId) }
            runCurrent()
            first.cancel()
            runCurrent()
            refresh.gate.complete(Unit)

            assertEquals("fresh", second.await())
            assertEquals(2, refresh.calls)
        }

    @Test
    fun an_unknown_account_has_no_token() =
        runTest {
            val tokens =
                AccountTokens(store, neverLive, mustNotRefresh, clockAt(FIXTURE_EXPIRY))

            assertNull(tokens.tokenFor(misha))
        }
}
