package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.core.domain.friends.FRIEND_PALETTE_SIZE
import monster.greyde.kachalochka.core.domain.friends.assignedColors
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import kotlin.random.Random
import kotlin.time.Clock

private const val FNV_OFFSET = 0x811C9DC5.toInt()
private const val FNV_PRIME = 0x01000193

/** Friends' palette indices, a personal setting kept in the viewer's synced profile. */
class FriendColorStore(
    private val profiles: ProfileRepository,
    private val clock: Clock,
    private val random: Random,
) {
    /**
     * Colours for [friends] as [owner] sees them, saving newly drawn ones into an existing
     * profile. It never creates one: a profile the device has not pulled yet would win over the
     * server's on push, wiping the nickname and colours chosen elsewhere.
     */
    suspend fun colorsFor(
        owner: UserId,
        friends: List<UserId>,
    ): Map<UserId, Int> {
        val profile = profiles.forOwner(owner)
        val colors =
            if (profile == null) {
                // Nothing keeps these, so every screen and platform must draw them alike.
                val sorted = friends.distinct().sortedBy { it.value }
                assignedColors(emptyMap(), sorted, FRIEND_PALETTE_SIZE, Random(seedOf(owner)))
            } else {
                val stored = profile.friendColors
                assignedColors(stored, friends, FRIEND_PALETTE_SIZE, random).also {
                    if (it != stored) save(owner, profile, it)
                }
            }
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

    // 32-bit FNV-1a, spelled out so the seed never depends on a platform's String.hashCode.
    private fun seedOf(owner: UserId): Int =
        owner.value.fold(FNV_OFFSET) { hash, char -> (hash xor char.code) * FNV_PRIME }

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
