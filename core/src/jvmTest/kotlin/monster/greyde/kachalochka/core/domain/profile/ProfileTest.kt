package monster.greyde.kachalochka.core.domain.profile

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class ProfileTest {
    private val now = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun a_signed_in_owner_s_new_profile_takes_the_owner_s_id() {
        val owner = UserId("11111111-1111-4111-8111-111111111111")

        val profile = Profile.new(owner, now)

        assertEquals(ProfileId(owner.value), profile.id)
        assertEquals(owner, profile.userId)
        assertEquals(now, profile.updatedAt)
        assertEquals(emptyMap(), profile.friendColors)
    }

    @Test
    fun anonymous_new_profiles_take_random_ids() {
        assertNotEquals(Profile.new(null, now).id, Profile.new(null, now).id)
    }
}
