package monster.greyde.kachalochka.core.data.friends

import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendResult
import monster.greyde.kachalochka.core.domain.friends.FriendVisit
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.GroupGym
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.identity.UserId

private val NO_GYM = GroupGym(emptyList(), emptyList(), emptyList())

/**
 * A trainee's friends stay the trainee's: read for a trainee [listed] on the device, under the
 * trainer's session, they would be mates of groups the two share, so there are none.
 */
class TraineeFriends(
    private val inner: FriendsRepository,
    private val listed: () -> List<Account>,
) : FriendsRepository by inner {
    private fun coached(viewer: UserId) = listed().any { it.userId == viewer && it.isTrainee }

    override suspend fun mates(viewer: UserId): List<Friend> =
        if (coached(viewer)) emptyList() else inner.mates(viewer)

    override suspend fun groupGym(viewer: UserId): GroupGym =
        if (coached(viewer)) NO_GYM else inner.groupGym(viewer)

    override suspend fun groupVisits(
        viewer: UserId,
        from: CalendarDay,
        to: CalendarDay,
    ): List<FriendVisit> =
        if (coached(viewer)) emptyList() else inner.groupVisits(viewer, from, to)

    override suspend fun groupLinks(viewer: UserId): List<MachineLink> =
        if (coached(viewer)) emptyList() else inner.groupLinks(viewer)

    override suspend fun groupPeaks(viewer: UserId): List<MachinePeaks> =
        if (coached(viewer)) emptyList() else inner.groupPeaks(viewer)

    override suspend fun latestOn(
        viewer: UserId,
        machines: Set<MachineId>,
    ): List<FriendResult> = if (coached(viewer)) emptyList() else inner.latestOn(viewer, machines)
}
