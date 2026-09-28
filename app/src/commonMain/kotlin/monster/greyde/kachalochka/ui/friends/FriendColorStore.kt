package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.core.domain.friends.FRIEND_PALETTE_SIZE
import monster.greyde.kachalochka.core.domain.friends.assignedColors
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import kotlin.random.Random
import kotlin.time.Clock

/** Friends' palette indices, a personal setting kept in the viewer's synced profile. */
class FriendColorStore(
    private val profiles: ProfileRepository,
    private val clock: Clock,
    private val random: Random,
) {
    /** Colours for [friends] as [owner] sees them, saving any newly drawn ones. */
    suspend fun colorsFor(
        owner: UserId,
        friends: List<UserId>,
    ): Map<UserId, Int> {
        val profile = profiles.forOwner(owner)
        val stored = profile?.friendColors.orEmpty()
        val colors = assignedColors(stored, friends, FRIEND_PALETTE_SIZE, random)
        if (colors != stored) save(owner, profile, colors)
        return colors.filterKeys { it in friends }
    }

    suspend fun set(
        owner: UserId,
        friend: UserId,
        index: Int,
    ) {
        val profile = profiles.forOwner(owner)
        save(owner, profile, profile?.friendColors.orEmpty() + (friend to index))
    }

    private suspend fun save(
        owner: UserId,
        profile: Profile?,
        colors: Map<UserId, Int>,
    ) {
        val now = clock.now()
        profiles.upsert(
            (profile ?: Profile.new(owner, now)).copy(friendColors = colors, updatedAt = now),
        )
    }
}
