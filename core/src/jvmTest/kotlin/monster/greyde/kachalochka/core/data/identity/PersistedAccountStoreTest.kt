package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private class FakeStorage(
    var value: String? = null,
) : AccountStorage {
    override fun read(): String? = value

    override suspend fun write(value: String) {
        this.value = value
    }
}

private fun session(
    id: String,
    name: String,
) = AccountSession(
    Account(UserId(id), "$name@example.test", name),
    accessToken = "access-$name",
    refreshToken = "refresh-$name",
    expiresAt = Instant.fromEpochSeconds(1_700_000_000),
)

private val ivan = session("11111111-1111-4111-8111-111111111111", "Ivan")
private val misha = session("22222222-2222-4222-8222-222222222222", "Misha")

class PersistedAccountStoreTest {
    @Test
    fun adding_an_account_makes_it_active() =
        runTest {
            val store = PersistedAccountStore(FakeStorage())
            store.add(ivan)
            assertEquals(listOf(ivan.account), store.accounts.value)
            assertEquals(ivan.account.userId, store.activeId.value)
        }

    @Test
    fun a_second_account_becomes_active_without_dropping_the_first() =
        runTest {
            val store = PersistedAccountStore(FakeStorage())
            store.add(ivan)
            store.add(misha)
            assertEquals(listOf(ivan.account, misha.account), store.accounts.value)
            assertEquals(misha.account.userId, store.activeId.value)
        }

    @Test
    fun switching_changes_only_the_active_id() =
        runTest {
            val store = PersistedAccountStore(FakeStorage())
            store.add(ivan)
            store.add(misha)
            store.switch(ivan.account.userId)
            assertEquals(ivan.account.userId, store.activeId.value)
            assertEquals(2, store.accounts.value.size)
        }

    @Test
    fun switching_to_an_unknown_id_is_ignored() =
        runTest {
            val store = PersistedAccountStore(FakeStorage())
            store.add(ivan)
            val unknownId = UserId("33333333-3333-4333-8333-333333333333")
            store.switch(unknownId)
            assertEquals(ivan.account.userId, store.activeId.value)
            assertEquals(listOf(ivan.account), store.accounts.value)
        }

    @Test
    fun removing_the_active_account_falls_back_to_the_first_remaining_one() =
        runTest {
            val store = PersistedAccountStore(FakeStorage())
            store.add(ivan)
            store.add(misha)
            store.remove(misha.account.userId)
            assertEquals(listOf(ivan.account), store.accounts.value)
            assertEquals(ivan.account.userId, store.activeId.value)
        }

    @Test
    fun removing_the_last_account_leaves_nobody_active() =
        runTest {
            val store = PersistedAccountStore(FakeStorage())
            store.add(ivan)
            store.remove(ivan.account.userId)
            assertEquals(emptyList(), store.accounts.value)
            assertNull(store.activeId.value)
        }

    @Test
    fun deactivating_keeps_the_accounts_and_leaves_nobody_active() =
        runTest {
            val store = PersistedAccountStore(FakeStorage())
            store.add(ivan)
            store.add(misha)
            store.deactivate()
            assertEquals(listOf(ivan.account, misha.account), store.accounts.value)
            assertNull(store.activeId.value)
            assertEquals(misha, store.sessionOf(misha.account.userId))
        }

    @Test
    fun a_deactivated_store_reopens_with_nobody_active() =
        runTest {
            val storage = FakeStorage()
            PersistedAccountStore(storage).run {
                add(ivan)
                deactivate()
            }
            assertNull(PersistedAccountStore(storage).activeId.value)
        }

    @Test
    fun accounts_and_the_active_id_survive_a_new_store_over_the_same_storage() =
        runTest {
            val storage = FakeStorage()
            PersistedAccountStore(storage).run {
                add(ivan)
                add(misha)
                switch(ivan.account.userId)
            }
            val reopened = PersistedAccountStore(storage)
            assertEquals(listOf(ivan.account, misha.account), reopened.accounts.value)
            assertEquals(ivan.account.userId, reopened.activeId.value)
            assertEquals(misha, reopened.sessionOf(misha.account.userId))
        }

    @Test
    fun replacing_a_session_keeps_the_order_and_the_active_account() =
        runTest {
            val store = PersistedAccountStore(FakeStorage())
            store.add(ivan)
            store.add(misha)
            store.switch(ivan.account.userId)

            store.replaceSession(misha.copy(accessToken = "fresh"))

            assertEquals(listOf(ivan.account, misha.account), store.accounts.value)
            assertEquals(ivan.account.userId, store.activeId.value)
            assertEquals("fresh", store.sessionOf(misha.account.userId)?.accessToken)
        }

    @Test
    fun replacing_the_session_of_an_unknown_account_adds_nothing() =
        runTest {
            val store = PersistedAccountStore(FakeStorage())
            store.add(ivan)

            store.replaceSession(misha)

            assertEquals(listOf(ivan.account), store.accounts.value)
            assertEquals(ivan.account.userId, store.activeId.value)
        }

    @Test
    fun a_replaced_session_survives_a_new_store_over_the_same_storage() =
        runTest {
            val storage = FakeStorage()
            val fresh = ivan.copy(accessToken = "fresh", refreshToken = "fresh-refresh")
            PersistedAccountStore(storage).run {
                add(ivan)
                replaceSession(fresh)
            }

            assertEquals(fresh, PersistedAccountStore(storage).sessionOf(ivan.account.userId))
        }

    @Test
    fun unreadable_storage_opens_empty_instead_of_failing() =
        runTest {
            val store = PersistedAccountStore(FakeStorage("not json"))
            assertEquals(emptyList(), store.accounts.value)
            assertNull(store.activeId.value)
        }
}
