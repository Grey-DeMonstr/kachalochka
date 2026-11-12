package monster.greyde.kachalochka.ui.visit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.friends.keptOnly
import monster.greyde.kachalochka.core.domain.friends.FriendRecord
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.friendRecords
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.machine.GroupMachines
import monster.greyde.kachalochka.ui.machine.MachineReads

/**
 * The group read for the active account and the friends' peaks on its machines, which wait for
 * that read: the kept peaks first, then, after a fresh group read, the server's. A read for an
 * owner asked before the last one is dropped.
 */
class GroupReads(
    private val friends: FriendsRepository,
    private val reads: MachineReads,
    private val scope: CoroutineScope,
    private val changed: () -> Unit,
) {
    /**
     * Group mates' machines, links and photos, read for [groupFor]; null before the read lands and
     * after a dropped read that failed again.
     */
    var group: GroupMachines? = null
        private set

    private var peaks: List<MachinePeaks> = emptyList()
    private var groupFor: UserId? = null
    private var loadingGroup: Job? = null
    private var loadingPeaks: Job? = null
    private var machine: MachineId? = null

    /** Links may have changed on another screen or in a sync, so the next follow reads again. */
    private var stale = false

    fun markStale() {
        stale = true
    }

    /** The friends' records on [machine], an own machine of [owner]. */
    fun follow(
        owner: UserId?,
        machine: MachineId?,
    ) {
        this.machine = machine
        if (owner != groupFor || stale) loadGroup(owner)
        stale = false
    }

    /** The records on the followed machine, a row for each friend's linked machine with sets. */
    fun records(): List<FriendRecord> {
        val open = machine ?: return emptyList()
        val read = group ?: return emptyList()
        return friendRecords(open, read.friends, read.clusters, peaks)
    }

    private fun loadPeaks(
        owner: UserId,
        fresh: Boolean,
    ) {
        loadingPeaks?.cancel()
        loadingPeaks =
            scope.launch {
                suspend fun show(read: suspend () -> List<MachinePeaks>) {
                    val found = catching { read() }
                    if (groupFor != owner) return
                    // A failed re-read keeps what an earlier read showed.
                    found.onSuccess {
                        peaks = it
                        changed()
                    }
                }
                show { keptOnly { friends.groupPeaks(owner) } }
                if (fresh) show { friends.groupPeaks(owner) }
            }
    }

    private fun loadGroup(owner: UserId?) {
        loadingGroup?.cancel()
        loadingPeaks?.cancel()
        if (owner != groupFor) {
            group = null
            peaks = emptyList()
        }
        groupFor = owner
        if (owner == null) return
        loadingGroup =
            scope.launch {
                reads.group(owner).collect { read ->
                    if (groupFor != owner) return@collect
                    group = read.machines
                    changed()
                    if (read.machines != null) loadPeaks(owner, read.fresh)
                }
            }
    }
}
