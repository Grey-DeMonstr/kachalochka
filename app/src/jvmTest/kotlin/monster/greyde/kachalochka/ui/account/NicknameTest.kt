package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private fun session(
    id: String,
    name: String,
) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

private val t0 = Instant.fromEpochSeconds(1_700_000_000)

class NicknameTest {
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")

    @Test
    fun a_profile_nickname_wins_over_the_account_name() =
        runTest {
            val gym = FakeGym().withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            gym.profiles.upsert(Profile.new(owner, gym.clock.now()).copy(displayName = "Ванёк"))
            val nickname = Nickname(gym.profiles, gym.accounts)

            assertEquals("Ванёк", nickname.of(owner))
        }

    @Test
    fun a_blank_nickname_falls_back_to_the_account_name() =
        runTest {
            val gym = FakeGym().withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            gym.profiles.upsert(Profile.new(owner, gym.clock.now()).copy(displayName = "   "))
            val nickname = Nickname(gym.profiles, gym.accounts)

            assertEquals("Иван", nickname.of(owner))
        }

    @Test
    fun no_owner_means_no_name() =
        runTest {
            val gym = FakeGym()
            val nickname = Nickname(gym.profiles, gym.accounts)

            assertEquals("", nickname.of(null))
        }
}
