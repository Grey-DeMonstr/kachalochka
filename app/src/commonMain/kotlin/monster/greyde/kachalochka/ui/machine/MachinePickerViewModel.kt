package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.friendMachineRows
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.calendarDaysBetween
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.gym.rankMachines
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.format.weightCaption
import kotlin.time.Clock
import kotlin.time.Instant

data class PickerUiState(
    val query: String = "",
    val createLabel: String? = null,
    val sectionLabel: String = "Недавние",
    val rows: List<PickerRowUi> = emptyList(),
    val friendRows: List<PickerRowUi> = emptyList(),
)

data class PickerRowUi(
    val id: MachineId,
    val name: String,
    val detail: String?,
)

class MachinePickerViewModel(
    private val day: CalendarDay,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val visits: VisitRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
    private val friends: FriendsRepository,
    private val machineLinks: MachineLinkRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var all: List<Machine> = emptyList()
    private var latest: Map<MachineId, WorkoutSet> = emptyMap()
    private var inVisit: Map<MachineId, Int> = emptyMap()
    private var shownFor: UserId? = null
    private var group: GroupMachines? = null
    private var friendsFor: UserId? = null
    private var loading: Job? = null
    private var loadingFriends: Job? = null

    /** Friends' machines read for any account but the one shown are never offered or cloned. */
    private val offeredFriends: List<FriendMachine>
        get() {
            val read =
                group?.takeIf { friendsFor != null && friendsFor == shownFor }
                    ?: return emptyList()
            return friendMachineRows(read.friends, all, read.clusters, read.links)
        }

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    /** Own machines show at once; friends' follow from the network, if it answers. */
    fun load() {
        loading?.cancel()
        loadingFriends?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                all = machines.all(owner)
                latest = sets.latestPerMachine(owner).associateBy { it.machineId }
                inVisit =
                    visits
                        .shownOn(owner, day, sets, utcOffset::at)
                        ?.let { sets.forVisit(it.id) }
                        .orEmpty()
                        .groupingBy { it.machineId }
                        .eachCount()
                shownFor = owner
                publish(mutableState.value.query)
                owner?.let(::loadFriends)
            }
    }

    private fun loadFriends(owner: UserId) {
        loadingFriends =
            viewModelScope.launch {
                val found = loadGroupMachines(owner, friends, machineLinks)
                if (currentUser.id() != owner) return@launch
                group = found
                friendsFor = owner
                publish(mutableState.value.query)
            }
    }

    fun onQueryChange(query: String) = publish(query)

    /** A friend's machine becomes the account's own, linked to theirs, before it is picked. */
    fun pickFriend(
        id: MachineId,
        onPicked: (MachineId) -> Unit,
    ) {
        val friend = offeredFriends.firstOrNull { it.machine.id == id } ?: return
        val offeredTo = friendsFor
        writes.launch {
            val owner = currentUser.id() ?: return@launch
            if (owner != offeredTo) return@launch
            val (copy, link) = linkedCopy(friend.machine, owner, clock.now())
            machines.upsert(copy)
            machineLinks.upsert(link)
            onPicked(copy.id)
        }
    }

    private fun publish(query: String) {
        val ranking = rankMachines(query, all, latest.mapValues { it.value.recordedAt })
        val now = clock.now()
        val needle = query.trim()
        val friendRows =
            offeredFriends
                .filter { it.machine.name.contains(needle, ignoreCase = true) }
                .map {
                    PickerRowUi(
                        it.machine.id,
                        it.machine.name,
                        "${it.owner.displayName} · ${weightCaption(it.machine)}",
                    )
                }
        mutableState.value =
            PickerUiState(
                query = query,
                createLabel = if (ranking.offerCreate) "Создать «${query.trim()}»" else null,
                sectionLabel = if (query.isBlank()) "Недавние" else "Похожие",
                rows = ranking.machines.map { PickerRowUi(it.id, it.name, detail(it, now)) },
                friendRows = friendRows,
            )
    }

    private fun detail(
        machine: Machine,
        now: Instant,
    ): String? {
        val offset = utcOffset.at(now)
        inVisit[machine.id]?.let {
            val where = if (day == CalendarDay.of(now, offset)) "сегодня" else "в этом визите"
            return "${setCount(it)} $where"
        }
        val last = latest[machine.id] ?: return null
        val days = calendarDaysBetween(last.recordedAt, now, offset)
        val value = setValue(last.weight, last.reps, unitLabel(machine))
        return "Было $value · ${daysAgoLabel(days)}"
    }
}
