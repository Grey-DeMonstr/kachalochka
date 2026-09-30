package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.calendarDaysBetween
import monster.greyde.kachalochka.core.domain.gym.rankMachines
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.friendMachineDetail
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock
import kotlin.time.Instant

data class PickerUiState(
    val query: String = "",
    val createLabel: String? = null,
    val sectionLabel: String = "",
    val rows: List<PickerRowUi> = emptyList(),
    val friendRows: List<PickerRowUi> = emptyList(),
)

data class PickerRowUi(
    val id: MachineId,
    val name: String,
    val detail: String?,
    val photo: Photo? = null,
)

class MachinePickerViewModel(
    private val day: CalendarDay?,
    private val sets: WorkoutSetRepository,
    private val visits: VisitRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
    private val profiles: ProfileRepository,
    private val catalogue: MachineCatalogue,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var own: OwnMachines? = null
    private var group: GroupMachines? = null
    private var latest: Map<MachineId, WorkoutSet> = emptyMap()
    private var inVisit: Map<MachineId, Int> = emptyMap()
    private var preferred = PreferredWeightUnit.Kg
    private var loading: Job? = null
    private var loadingFriends: Job? = null

    private val shown: ShownMachines? get() = own?.let { ShownMachines(it, group) }

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
                own = catalogue.own(owner)
                latest = sets.latestPerMachine(owner).associateBy { it.machineId }
                inVisit =
                    day
                        ?.let { visits.shownOn(owner, it, sets, utcOffset::at) }
                        ?.sets
                        .orEmpty()
                        .groupingBy { it.machineId }
                        .eachCount()
                preferred = profiles.preferredUnit(owner)
                publish(mutableState.value.query)
                owner?.let(::loadFriends)
            }
    }

    private fun loadFriends(owner: UserId) {
        loadingFriends =
            viewModelScope.launch {
                group = reading { catalogue.group(owner) }.getOrNull()
                publish(mutableState.value.query)
            }
    }

    fun onQueryChange(query: String) = publish(query)

    /** A friend's machine becomes the account's own, linked to theirs, before it is picked. */
    fun pickFriend(
        id: MachineId,
        onPicked: (MachineId) -> Unit,
    ) {
        val offered = shown ?: return
        val friend = offered.offered.firstOrNull { it.machine.id == id } ?: return
        val offeredTo = offered.own.owner
        writes.launch {
            val owner = currentUser.id() ?: return@launch
            if (owner != offeredTo) return@launch
            onPicked(catalogue.take(owner, friend.machine).id)
        }
    }

    private fun publish(query: String) {
        val shown = shown
        val ranking =
            rankMachines(
                query,
                shown?.own?.machines.orEmpty(),
                latest.mapValues { it.value.recordedAt },
            )
        val now = clock.now()
        val needle = query.trim()
        val friendRows =
            shown
                ?.let { s ->
                    s.offered
                        .filter { it.machine.name.contains(needle, ignoreCase = true) }
                        .map {
                            PickerRowUi(
                                it.machine.id,
                                it.machine.name,
                                friendMachineDetail(it, preferred),
                                s.cover(it.machine.id),
                            )
                        }
                }.orEmpty()
        mutableState.value =
            PickerUiState(
                query = query,
                createLabel =
                    if (ranking.offerCreate) {
                        AppStrings.current.createNamed(
                            query.trim(),
                        )
                    } else {
                        null
                    },
                sectionLabel =
                    if (query.isBlank()) AppStrings.current.recent else AppStrings.current.similar,
                rows =
                    ranking.machines.map {
                        PickerRowUi(it.id, it.name, detail(it, now), shown?.cover(it.id))
                    },
                friendRows = friendRows,
            )
    }

    private fun detail(
        machine: Machine,
        now: Instant,
    ): String? {
        val offset = utcOffset.at(now)
        inVisit[machine.id]?.let {
            val where =
                if (day ==
                    CalendarDay.of(now, offset)
                ) {
                    AppStrings.current.todayLower
                } else {
                    AppStrings.current.inThisVisit
                }
            return "${setCount(it)} $where"
        }
        val last = latest[machine.id] ?: return null
        val days = calendarDaysBetween(last.recordedAt, now, offset)
        val value = setValue(last.weight, last.reps, machine, preferred)
        return AppStrings.current.wasAgo(value, daysAgoLabel(days))
    }
}
