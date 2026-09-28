package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.core.domain.friends.FRIEND_PALETTE_SIZE
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
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
    private val mates: FriendsRepository,
) {
    /**
     * Colours for [friends] as [owner] sees them. Friends without a stored colour get one drawn
     * from the owner and all of their group mates, so every screen and platform shows the same
     * colour without writing it: a profile the device has not pulled yet would win over the
     * server's on push. Reads the group mates online.
     */
    suspend fun colorsFor(
        owner: UserId,
        friends: List<UserId>,
    ): Map<UserId, Int> {
        val stored = profiles.forOwner(owner)?.friendColors.orEmpty()
        val everyone = mates.mates(owner).map { it.userId } + friends
        val sorted = everyone.distinct().sortedBy { it.value }
        val colors = assignedColors(stored, sorted, FRIEND_PALETTE_SIZE, Random(seedOf(owner)))
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
