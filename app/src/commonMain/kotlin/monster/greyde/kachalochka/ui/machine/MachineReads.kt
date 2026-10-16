package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.friends.reading

/** A group read for [owner]; [machines] is the last good one, null before any. */
data class GroupRead(
    val owner: UserId,
    val machines: GroupMachines?,
    /** [machines] came from the server in this read. */
    val fresh: Boolean,
    /** The server did not answer this read. */
    val offline: Boolean,
)

/** What a machine screen shows at one moment: the own machines and the group read so far. */
data class MachineRead(
    val shown: ShownMachines,
    /** Null while the first group read is in flight with nothing cached, and signed out. */
    val group: GroupRead?,
    val ownPeaks: List<MachinePeaks> = emptyList(),
    val friendPeaks: List<MachinePeaks> = emptyList(),
    val colors: Map<UserId, Int> = emptyMap(),
) {
    val offline: Boolean get() = group?.offline == true

    /** Signed in, with no group read yet to show. */
    val waiting: Boolean get() = shown.own.owner != null && group == null
}

/**
 * The one way a machine screen reads the account's machines and its group's: own machines at
 * once, the last group read next, the fresh one last. A flow ends when [owner] stops being the
 * active account.
 */
class MachineReads(
    private val catalogue: MachineCatalogue,
    private val accounts: Accounts,
    private val sets: WorkoutSetRepository,
    private val friends: FriendsRepository,
    private val friendColors: FriendColorStore,
) {
    private val lastPeaks = MutableStateFlow<Map<UserId, List<MachinePeaks>>>(emptyMap())
    private val lastColors = MutableStateFlow<Map<UserId, Map<UserId, Int>>>(emptyMap())

    /** The last group read, when there is one, then the fresh read. */
    fun group(owner: UserId): Flow<GroupRead> =
        flow {
            if (!isActive(owner)) return@flow
            val cached = cachedRead(owner)
            if (!isActive(owner)) return@flow
            if (cached != null) emit(cached)
            if (!isActive(owner)) return@flow
            val fresh = freshRead(owner, cached)
            if (isActive(owner)) emit(fresh)
        }

    /**
     * The own machines first, then each group read joined with them. With [withCards], the
     * friends' records and colours are read after the last group read and sent in one more.
     */
    fun machines(
        owner: UserId?,
        withCards: Boolean = false,
    ): Flow<MachineRead> =
        flow {
            if (!isActive(owner)) return@flow
            val own = catalogue.own(owner)
            val ownPeaks = if (withCards) sets.peaks(owner) else emptyList()

            suspend fun show(group: GroupRead?): MachineRead? {
                if (!isActive(owner)) return null
                val shown = ShownMachines(own, group?.machines)
                val read =
                    MachineRead(
                        shown,
                        group,
                        ownPeaks,
                        owner?.takeIf { withCards }?.let { lastPeaks.value[it] }.orEmpty(),
                        owner?.takeIf { withCards }?.let { lastColors.value[it] }.orEmpty(),
                    )
                emit(read)
                return read
            }
            if (owner == null) {
                show(null)
                return@flow
            }
            val cached = cachedRead(owner)
            show(cached) ?: return@flow
            val last = show(freshRead(owner, cached)) ?: return@flow
            if (!withCards) return@flow
            keepCards(owner, last)
            show(last.group)
        }

    private suspend fun keepCards(
        owner: UserId,
        last: MachineRead,
    ) {
        if (!isActive(owner)) return
        val peaks = reading { friends.groupPeaks(owner) }
        if (!isActive(owner)) return
        peaks.onSuccess { found -> lastPeaks.update { it + (owner to found) } }
        val offered =
            last.shown.offered
                .map { it.owner.userId }
                .distinct()
        val colors = reading { friendColors.colorsFor(owner, offered) }
        if (!isActive(owner)) return
        colors.onSuccess { found -> lastColors.update { it + (owner to found) } }
    }

    private suspend fun cachedRead(owner: UserId): GroupRead? =
        reading { catalogue.lastGroup(owner) }
            .getOrNull()
            ?.let { GroupRead(owner, it, fresh = false, offline = false) }

    /** A failed read falls back to the last good one, else to [cached]. */
    private suspend fun freshRead(
        owner: UserId,
        cached: GroupRead?,
    ): GroupRead =
        reading { catalogue.group(owner) }.fold(
            { GroupRead(owner, it, fresh = true, offline = false) },
            {
                val last = reading { catalogue.lastGroup(owner) }.getOrNull() ?: cached?.machines
                GroupRead(owner, last, fresh = false, offline = true)
            },
        )

    private fun isActive(owner: UserId?) = accounts.activeId.value == owner
}
