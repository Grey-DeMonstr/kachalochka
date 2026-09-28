package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private val MISHA_SESSION =
    AccountSession(
        Account(UserId("22222222-2222-4222-8222-222222222222"), "misha@example.test", "Миша"),
        "access",
        "refresh",
        IVAN_SESSION.expiresAt,
    )
private val MISHA = Friend(MISHA_SESSION.account.userId, "Миша")

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsCacheTest {
    private val gym = FakeGym().withAccounts(IVAN_SESSION, MISHA_SESSION, active = IVAN_SESSION)

    @Test
    fun warming_reads_the_active_account_s_groups() =
        runTest {
            gym.friends.group("Зал на Лесной", owner = ME)
            val cache = GroupsCache(gym.friends, gym.accounts, backgroundScope)

            cache.warm()
            runCurrent()

            assertEquals(listOf("Зал на Лесной"), cache.cached(ME.userId)?.map { it.name })
        }

    @Test
    fun switching_account_reads_the_new_one_and_keeps_the_old_list() =
        runTest {
            gym.friends.group("Зал на Лесной", owner = ME)
            gym.friends.group("Бассейн", owner = MISHA)
            val cache = GroupsCache(gym.friends, gym.accounts, backgroundScope)
            cache.warm()
            runCurrent()

            gym.accounts.switchTo(MISHA.userId)
            runCurrent()

            assertEquals(listOf("Бассейн"), cache.cached(MISHA.userId)?.map { it.name })
            assertEquals(listOf("Зал на Лесной"), cache.cached(ME.userId)?.map { it.name })
        }

    @Test
    fun an_offline_read_ahead_leaves_the_cache_empty_and_keeps_warming() =
        runTest {
            gym.friends.offline = true
            val cache = GroupsCache(gym.friends, gym.accounts, backgroundScope)

            cache.warm()
            runCurrent()
            assertNull(cache.cached(ME.userId))

            gym.friends.offline = false
            gym.accounts.switchTo(MISHA.userId)
            runCurrent()
            assertEquals(emptyList(), cache.cached(MISHA.userId))
        }

    @Test
    fun an_offline_refresh_throws_and_keeps_what_was_read_before() =
        runTest {
            gym.friends.group("Зал на Лесной", owner = ME)
            val cache = GroupsCache(gym.friends, gym.accounts, backgroundScope)
            cache.refresh(ME.userId)

            gym.friends.offline = true

            assertFailsWith<IllegalStateException> { cache.refresh(ME.userId) }
            assertEquals(listOf("Зал на Лесной"), cache.cached(ME.userId)?.map { it.name })
        }
}
