package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertFailsWith

private class CancelledProfiles(
    inner: ProfileRepository,
) : ProfileRepository by inner {
    override suspend fun forOwner(owner: UserId?): Profile? = throw CancellationException("left")
}

class AccountAvatarsTest {
    @Test
    fun a_cancelled_read_cancels_the_refresh() =
        runTest {
            val gym = signedInGym()
            val avatars = AccountAvatars(gym.accounts, CancelledProfiles(gym.profiles))

            assertFailsWith<CancellationException> { avatars.refresh() }
        }
}
