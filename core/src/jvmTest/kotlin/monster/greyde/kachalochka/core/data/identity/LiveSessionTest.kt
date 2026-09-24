package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.identity.LiveSessionChange.Ended
import monster.greyde.kachalochka.core.data.identity.LiveSessionChange.Renewed
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class LoggedActivation : SessionActivation {
    val activated = mutableListOf<UserId>()
    var clears = 0
    val cleared get() = clears > 0
    var refusal: Throwable? = null

    override suspend fun activate(session: AccountSession) {
        refusal?.let { throw it }
        activated += session.account.userId
    }

    override suspend fun clear() {
        clears++
    }
}

class LiveSessionTest {
    private val ivan = accountSession("11111111-1111-4111-8111-111111111111", "Ivan")
    private val misha = accountSession("22222222-2222-4222-8222-222222222222", "Misha")
    private val store = PersistedAccountStore(InMemoryAccountStorage())
    private val inner = LoggedActivation()
    private val live = LiveSession(inner, store)

    @Test
    fun a_refreshed_live_session_is_written_back() =
        runTest {
            store.add(ivan)
            store.add(misha)
            store.switch(ivan.account.userId)
            live.activate(ivan)
            val fresh = ivan.copy(accessToken = "fresh", refreshToken = "fresh-refresh")

            live.follow(flowOf(Renewed(fresh)))

            assertEquals(fresh, store.sessionOf(ivan.account.userId))
            assertEquals(listOf(ivan.account, misha.account), store.accounts.value)
            assertEquals(ivan.account.userId, store.activeId.value)
        }

    @Test
    fun a_session_signed_in_but_not_yet_stored_is_left_out() =
        runTest {
            store.add(ivan)

            live.follow(flowOf(Renewed(misha)))

            assertEquals(listOf(ivan.account), store.accounts.value)
        }

    @Test
    fun an_account_whose_live_session_the_library_ends_is_signed_out() =
        runTest {
            store.add(ivan)
            store.add(misha)
            live.activate(misha)

            live.follow(flowOf(Renewed(misha), Ended))

            assertEquals(listOf(ivan.account), store.accounts.value)
            assertEquals(ivan.account.userId, store.activeId.value)
            assertEquals(ivan.account.userId, inner.activated.last())
        }

    @Test
    fun the_last_account_signed_out_that_way_leaves_nobody_live() =
        runTest {
            store.add(ivan)
            live.activate(ivan)

            live.follow(flowOf(Renewed(ivan), Ended))

            assertEquals(emptyList(), store.accounts.value)
            assertTrue(inner.cleared)
        }

    @Test
    fun clearing_on_purpose_signs_nobody_out() =
        runTest {
            store.add(ivan)
            live.activate(ivan)

            live.follow(
                flow {
                    emit(Renewed(ivan))
                    store.deactivate()
                    live.clear()
                    emit(Ended)
                },
            )

            assertEquals(listOf(ivan.account), store.accounts.value)
            assertEquals(listOf(ivan.account.userId), inner.activated)
            assertEquals(1, inner.clears)
        }

    @Test
    fun an_end_before_anything_was_live_signs_nobody_out() =
        runTest {
            store.add(ivan)

            live.follow(flowOf(Ended))

            assertEquals(listOf(ivan.account), store.accounts.value)
            assertEquals(ivan.account.userId, store.activeId.value)
        }

    @Test
    fun an_end_trailing_a_switch_signs_nobody_out() =
        runTest {
            store.add(ivan)
            store.add(misha)
            live.activate(ivan)

            live.follow(
                flow {
                    emit(Renewed(ivan))
                    live.activate(misha)
                    emit(Ended)
                },
            )

            assertEquals(listOf(ivan.account, misha.account), store.accounts.value)
        }

    @Test
    fun a_refused_activation_does_not_stop_the_follower() =
        runTest {
            store.add(ivan)
            store.add(misha)
            live.activate(misha)

            live.follow(
                flow {
                    emit(Renewed(misha))
                    inner.refusal = IllegalStateException("no connectivity")
                    emit(Ended)
                    emit(Renewed(ivan.copy(accessToken = "fresh")))
                },
            )

            assertEquals(listOf(ivan.account), store.accounts.value)
            assertEquals("fresh", store.sessionOf(ivan.account.userId)?.accessToken)
        }

    @Test
    fun a_sign_out_whose_next_account_is_refused_leaves_nobody_live() =
        runTest {
            store.add(ivan)
            store.add(misha)
            live.activate(misha)

            live.follow(
                flow {
                    emit(Renewed(misha))
                    inner.refusal = IllegalStateException("no connectivity")
                    emit(Ended)
                },
            )

            assertEquals(listOf(ivan.account), store.accounts.value)
            assertNull(store.activeId.value)
            assertTrue(inner.cleared)
        }

    @Test
    fun a_refused_switch_keeps_the_live_account_s_token() =
        runTest {
            live.activate(ivan)
            live.follow(flowOf(Renewed(ivan)))
            inner.refusal = IllegalStateException("no connectivity")

            assertFailsWith<IllegalStateException> { live.activate(misha) }

            assertEquals(ivan.accessToken, live.accessTokenOf(ivan.account.userId))
        }

    @Test
    fun the_live_token_is_offered_only_for_the_account_that_is_live() =
        runTest {
            live.activate(ivan)

            live.follow(flowOf(Renewed(ivan)))

            assertEquals(ivan.accessToken, live.accessTokenOf(ivan.account.userId))
            assertNull(live.accessTokenOf(misha.account.userId))
        }
}
