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
    private val misha = accountSession("22222222-2222-4222-8222-222222222222", "Misha")
    private val signIns = mutableListOf(ivan)
    private val steps = mutableListOf<String>()
    private val server = RecordingServer(steps)
    private val store = PersistedAccountStore(InMemoryAccountStorage())
    private val accounts =
        Accounts(
            store,
            object : GoogleSignIn {
                override suspend fun signIn() = signIns.removeFirst()
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

    @Test
    fun the_children_the_deleted_account_acted_for_leave_the_device_too() =
        runTest {
            accounts.addAccount()
            store.setManaged(listOf(managedChild(ivan)))

            deletion.delete(ivan.account.userId)

            val id = ivan.account.userId.value
            assertEquals(
                listOf("server $id", "device $id", "device ${SASHA_ID.value}", "signed out"),
                steps,
            )
            assertEquals(emptyList(), store.accounts.value)
        }

    @Test
    fun a_child_stays_while_another_signed_in_parent_may_guard_it() =
        runTest {
            signIns += misha
            accounts.addAccount()
            accounts.addAccount()
            store.setManaged(listOf(managedChild(ivan)))
            steps.clear()

            deletion.delete(ivan.account.userId)

            val id = ivan.account.userId.value
            assertEquals(listOf("server $id", "device $id"), steps)
            assertEquals(listOf(misha.account.userId), store.accounts.value.map { it.userId })
        }

    @Test
    fun a_managed_child_is_never_deleted_as_its_guardian() =
        runTest {
            accounts.addAccount()
            store.setManaged(listOf(managedChild(ivan)))
            accounts.switchTo(SASHA_ID)
            steps.clear()

            assertFailsWith<IllegalStateException> { deletion.delete(SASHA_ID) }

            assertEquals(emptyList(), steps)
            assertEquals(SASHA_ID, store.activeId.value)
            assertEquals(2, store.accounts.value.size)
        }
}
