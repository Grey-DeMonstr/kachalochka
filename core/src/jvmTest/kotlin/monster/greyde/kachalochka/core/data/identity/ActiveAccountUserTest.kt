package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private class MemoryStorage : AccountStorage {
    private var value: String? = null

    override fun read(): String? = value

    override suspend fun write(value: String) {
        this.value = value
    }
}

private fun account(
    id: String,
    name: String,
) = AccountSession(
    Account(UserId(id), "$name@example.test", name),
    "access",
    "refresh",
    Instant.fromEpochSeconds(1_700_000_000),
)

class ActiveAccountUserTest {
    private val ivan = account("11111111-1111-4111-8111-111111111111", "Ivan")
    private val misha = account("22222222-2222-4222-8222-222222222222", "Misha")

    @Test
    fun nobody_owns_rows_until_an_account_is_added() =
        runTest {
            val store = PersistedAccountStore(MemoryStorage())
            assertNull(ActiveAccountUser(store).id())
        }

    @Test
    fun the_active_account_owns_rows_written_now() =
        runTest {
            val store = PersistedAccountStore(MemoryStorage())
            val user = ActiveAccountUser(store)
            store.add(ivan)
            store.add(misha)
            assertEquals(misha.account.userId, user.id())
            store.switch(ivan.account.userId)
            assertEquals(ivan.account.userId, user.id())
        }
}
