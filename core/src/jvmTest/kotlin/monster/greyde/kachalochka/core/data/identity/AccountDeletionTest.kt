package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private class RecordingServer(
    private val steps: MutableList<String>,
) : AccountServer {
    var offline = false

    override suspend fun deleteEverything(owner: UserId) {
        if (offline) error("no connection")
        steps += "server ${owner.value}"
    }
}

private class RecordingPurge(
    private val steps: MutableList<String>,
) : OwnedRowsPurge {
    override suspend fun purge(owner: UserId) {
        steps += "device ${owner.value}"
    }
}

class AccountDeletionTest {
    private val ivan = accountSession("11111111-1111-4111-8111-111111111111", "Ivan")
    private val steps = mutableListOf<String>()
    private val server = RecordingServer(steps)
    private val store = PersistedAccountStore(InMemoryAccountStorage())
    private val accounts =
        Accounts(
            store,
            object : GoogleSignIn {
                override suspend fun signIn() = ivan
            },
            object : SessionActivation {
                override suspend fun activate(session: AccountSession) = Unit

                override suspend fun clear() {
                    steps += "signed out"
                }
            },
            object : OwnerlessRows {
                override suspend fun claim(owner: UserId) = Unit
            },
        )
    private val deletion = AccountDeletion(server, RecordingPurge(steps), accounts)

    @Test
    fun the_server_goes_first_then_the_device_then_the_session() =
        runTest {
            accounts.addAccount()

            deletion.delete(ivan.account.userId)

            val id = ivan.account.userId.value
            assertEquals(listOf("server $id", "device $id", "signed out"), steps)
            assertNull(store.activeId.value)
        }

    @Test
    fun offline_nothing_changes_on_the_device() =
        runTest {
            accounts.addAccount()
            server.offline = true

            assertFailsWith<IllegalStateException> { deletion.delete(ivan.account.userId) }

            assertEquals(emptyList(), steps)
            assertEquals(ivan.account.userId, store.activeId.value)
        }
}
