package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.InMemoryAccountStorage
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class RecordingActivation : SessionActivation {
    val activated = mutableListOf<UserId>()
    var cleared = false

    override suspend fun activate(session: AccountSession) {
        activated += session.account.userId
    }

    override suspend fun clear() {
        cleared = true
    }
}

private fun session(expiresAt: Instant) =
    AccountSession(
        Account(UserId("11111111-1111-4111-8111-111111111111"), "ivan@example.test", "Иван"),
        "access",
        "refresh",
        expiresAt,
    )

class PendingSignInTest {
    private val ivan = session(Instant.fromEpochSeconds(1_700_000_000))
    private val reported = mutableListOf<String>()

    @Test
    fun a_session_brought_back_from_google_becomes_the_active_account() =
        runTest {
            val store = PersistedAccountStore(InMemoryAccountStorage())
            val activation = RecordingActivation()

            assertTrue(completeSignIn(ivan, store, activation))
            assertEquals(listOf(ivan.account), store.accounts.value)
            assertEquals(listOf(ivan.account.userId), activation.activated)
        }

    @Test
    fun a_page_that_came_back_from_nowhere_signs_nobody_in() =
        runTest {
            val store = PersistedAccountStore(InMemoryAccountStorage())
            val activation = RecordingActivation()

            assertFalse(completeSignIn(null, store, activation))
            assertEquals(emptyList(), store.accounts.value)
            assertEquals(emptyList(), activation.activated)
        }

    @Test
    fun a_reload_puts_the_stored_session_back_on_the_wire() =
        runTest {
            val storage = InMemoryAccountStorage()
            PersistedAccountStore(storage).add(ivan)
            val reloaded = PersistedAccountStore(storage)
            val activation = RecordingActivation()

            assertTrue(resumeActiveAccount(reloaded, activation))
            assertEquals(listOf(ivan.account.userId), activation.activated)
        }

    @Test
    fun a_reload_with_no_account_activates_nothing() =
        runTest {
            val store = PersistedAccountStore(InMemoryAccountStorage())
            val activation = RecordingActivation()

            assertFalse(resumeActiveAccount(store, activation))
            assertEquals(emptyList(), activation.activated)
        }

    @Test
    fun a_stored_session_past_its_expiry_is_still_handed_to_supabase() =
        runTest {
            val expired = session(Instant.fromEpochSeconds(1))
            val storage = InMemoryAccountStorage()
            PersistedAccountStore(storage).add(expired)
            val activation = RecordingActivation()

            assertTrue(resumeActiveAccount(PersistedAccountStore(storage), activation))
            assertEquals(listOf(expired.account.userId), activation.activated)
        }

    @Test
    fun a_restore_that_failed_leaves_nobody_claiming_to_be_signed_in() =
        runTest {
            val storage = InMemoryAccountStorage()
            PersistedAccountStore(storage).add(ivan)
            val reloaded = PersistedAccountStore(storage)
            val activation = RecordingActivation()

            val live =
                restoreSession(reloaded, activation, reported::add) {
                    error("Supabase returned a user without an e-mail")
                }

            assertFalse(live)
            assertNull(reloaded.activeId.value)
            assertTrue(activation.cleared)
            assertEquals(1, reported.size)
        }

    @Test
    fun an_account_that_could_not_be_restored_stays_available_to_sign_in_again() =
        runTest {
            val storage = InMemoryAccountStorage()
            PersistedAccountStore(storage).add(ivan)
            val reloaded = PersistedAccountStore(storage)

            restoreSession(reloaded, RecordingActivation(), reported::add) { error("no network") }

            assertEquals(listOf(ivan.account), reloaded.accounts.value)
        }

    @Test
    fun a_restore_that_worked_says_nothing_and_leaves_the_account_active() =
        runTest {
            val storage = InMemoryAccountStorage()
            PersistedAccountStore(storage).add(ivan)
            val reloaded = PersistedAccountStore(storage)
            val activation = RecordingActivation()

            assertTrue(restoreSession(reloaded, activation, reported::add) { null })
            assertEquals(ivan.account.userId, reloaded.activeId.value)
            assertFalse(activation.cleared)
            assertEquals(emptyList(), reported)
        }

    @Test
    fun a_first_visit_ends_signed_out_without_complaining() =
        runTest {
            val store = PersistedAccountStore(InMemoryAccountStorage())
            val activation = RecordingActivation()

            assertFalse(restoreSession(store, activation, reported::add) { null })
            assertNull(store.activeId.value)
            assertEquals(emptyList(), reported)
        }

    @Test
    fun a_start_up_that_never_answered_leaves_nobody_claiming_to_be_signed_in() =
        runTest {
            val storage = InMemoryAccountStorage()
            PersistedAccountStore(storage).add(ivan)
            val reloaded = PersistedAccountStore(storage)
            val activation = RecordingActivation()

            disownActiveAccount(reloaded, activation)

            assertNull(reloaded.activeId.value)
            assertTrue(activation.cleared)
            assertEquals(listOf(ivan.account), reloaded.accounts.value)
        }
}
