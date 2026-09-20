package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class QueuedSignIn(
    private val queue: MutableList<AccountSession>,
) : GoogleSignIn {
    override suspend fun signIn(): AccountSession = queue.removeFirst()
}

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

private class RecordingOwnerless : OwnerlessRows {
    val claims = mutableListOf<UserId>()

    override suspend fun claim(owner: UserId) {
        claims += owner
    }
}

private fun session(
    id: String,
    name: String,
) = AccountSession(
    Account(UserId(id), "$name@example.test", name),
    "access-$name",
    "refresh-$name",
    Instant.fromEpochSeconds(1_700_000_000),
)

class AccountsTest {
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Ivan")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Misha")

    private fun accounts(
        queue: MutableList<AccountSession>,
        activation: RecordingActivation = RecordingActivation(),
        ownerless: RecordingOwnerless = RecordingOwnerless(),
    ) = Accounts(
        PersistedAccountStore(InMemoryAccountStorage()),
        QueuedSignIn(queue),
        activation,
        ownerless,
    )

    @Test
    fun the_first_account_claims_the_ownerless_rows() =
        runTest {
            val ownerless = RecordingOwnerless()
            val service = accounts(mutableListOf(ivan), ownerless = ownerless)
            service.addAccount()
            assertEquals(listOf(ivan.account.userId), ownerless.claims)
        }

    @Test
    fun a_second_account_claims_nothing() =
        runTest {
            val ownerless = RecordingOwnerless()
            val service = accounts(mutableListOf(ivan, misha), ownerless = ownerless)
            service.addAccount()
            service.addAccount()
            assertEquals(listOf(ivan.account.userId), ownerless.claims)
        }

    @Test
    fun adding_an_account_makes_its_session_live() =
        runTest {
            val activation = RecordingActivation()
            val service = accounts(mutableListOf(ivan), activation = activation)
            service.addAccount()
            assertEquals(listOf(ivan.account.userId), activation.activated)
        }

    @Test
    fun switching_activates_the_stored_session_without_signing_in_again() =
        runTest {
            val activation = RecordingActivation()
            val queue = mutableListOf(ivan, misha)
            val service = accounts(queue, activation = activation)
            service.addAccount()
            service.addAccount()
            service.switchTo(ivan.account.userId)

            assertEquals(ivan.account.userId, service.activeId.value)
            assertEquals(ivan.account.userId, activation.activated.last())
            assertTrue(queue.isEmpty())
        }

    @Test
    fun signing_out_of_the_last_account_clears_the_live_session() =
        runTest {
            val activation = RecordingActivation()
            val service = accounts(mutableListOf(ivan), activation = activation)
            service.addAccount()
            service.signOut(ivan.account.userId)

            assertNull(service.activeId.value)
            assertTrue(activation.cleared)
        }

    @Test
    fun signing_out_of_the_active_account_activates_the_one_that_remains() =
        runTest {
            val activation = RecordingActivation()
            val service = accounts(mutableListOf(ivan, misha), activation = activation)
            service.addAccount()
            service.addAccount()
            service.signOut(misha.account.userId)

            assertEquals(ivan.account.userId, service.activeId.value)
            assertEquals(ivan.account.userId, activation.activated.last())
        }
}
