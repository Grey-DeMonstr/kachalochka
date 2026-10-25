package monster.greyde.kachalochka.ui.visit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.domain.friends.FriendResult
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.machine.GroupMachines
import monster.greyde.kachalochka.ui.machine.MachineReads

/**
 * The group read for the active account and the friends' latest results on its open machine, which
 * wait for that read. A read for an owner or machine asked before the last one is dropped.
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
    var results: List<FriendResult> = emptyList()
        private set

    private var groupFor: UserId? = null
    private var loadingGroup: Job? = null
    private var asked: Pair<UserId, MachineId>? = null
    private var loadingResults: Job? = null
    private var own: Set<MachineId> = emptySet()

    /** Links may have changed on another screen or in a sync, so the next follow reads again. */
    private var stale = false

    fun markStale() {
        stale = true
    }

    /** Asks for the results on [machine], an own machine of [owner], whose machines are [own]. */
    fun follow(
        owner: UserId?,
        machine: MachineId?,
        own: Set<MachineId>,
    ) {
        this.own = own
        val next = owner?.let { me -> machine?.let { me to it } }
        val readGroup = owner != groupFor || stale
        if (next != asked) {
            results = emptyList()
            loadingResults?.cancel()
            asked = next
            if (!readGroup) loadResults()
        }
        if (readGroup) loadGroup(owner)
        stale = false
    }

    private fun loadResults() {
        val asked = asked ?: return
        val (owner, machine) = asked
        val clusters = group?.takeIf { it.owner == owner }?.clusters ?: return
        val theirs = clusters.of(machine) - own
        loadingResults?.cancel()
        loadingResults =
            scope.launch {
                val read =
                    catching {
                        if (theirs.isEmpty()) emptyList() else friends.latestOn(owner, theirs)
                    }
                if (this@GroupReads.asked != asked) return@launch
                // A failed re-read keeps what an earlier read of the same machine showed.
                read.onSuccess { found ->
                    results = found
                    changed()
                }
            }
    }

    private fun loadGroup(owner: UserId?) {
        loadingGroup?.cancel()
        if (owner != groupFor) group = null
        groupFor = owner
        if (owner == null) return
        loadingGroup =
            scope.launch {
                reads.group(owner).collect { read ->
                    if (groupFor != owner) return@collect
                    group = read.machines
                    changed()
                    if (read.fresh) loadResults()
                }
            }
    }
}
