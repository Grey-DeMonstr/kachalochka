package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.calendarDaysBetween
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.gym.rankMachines
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.reading
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
) : ViewModel() {
    private val mutableState = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var all: List<Machine> = emptyList()
    private var latest: Map<MachineId, WorkoutSet> = emptyMap()
    private var inVisit: Map<MachineId, Int> = emptyMap()
    private var friendMachines: List<FriendMachine> = emptyList()

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    fun load() {
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
            friendMachines =
                owner
                    ?.let { reading { friends.groupMachines(it) }.getOrDefault(emptyList()) }
                    .orEmpty()
            publish(mutableState.value.query)
        }
    }

    fun onQueryChange(query: String) = publish(query)

    /** A friend's machine becomes the account's own, linked to theirs, before it is picked. */
    fun pickFriend(
        id: MachineId,
        onPicked: (MachineId) -> Unit,
    ) {
        val friend = friendMachines.firstOrNull { it.machine.id == id } ?: return
        writes.launch {
            val owner = currentUser.id() ?: return@launch
            val copy = linkedCopy(friend.machine, owner, clock.now())
            machines.upsert(copy)
            onPicked(copy.id)
        }
    }

    private fun publish(query: String) {
        val ranking = rankMachines(query, all, latest.mapValues { it.value.recordedAt })
        val now = clock.now()
        val ownKeys = all.map { it.linkKey }.toSet()
        val needle = query.trim()
        val friendRows =
            friendMachines
                .filter { it.machine.linkKey !in ownKeys }
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
