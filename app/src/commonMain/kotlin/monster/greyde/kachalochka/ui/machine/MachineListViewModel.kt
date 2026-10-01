package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.machineOrder
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.friends.reading
import kotlin.time.Clock

/** [own] is null until the account's machines are read. */
data class MachineListUiState(
    val own: List<MachineCardUi>? = null,
    val friendSections: List<FriendSectionUi> = emptyList(),
    val sort: MachineSort = MachineSort.Recent,
) {
    val friends: List<MachineCardUi> get() = friendSections.flatMap { it.cards }
}

class MachineListViewModel(
    private val catalogue: MachineCatalogue,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
    private val profiles: ProfileRepository,
    private val sets: WorkoutSetRepository,
    private val friendsRepository: FriendsRepository,
    private val friendColors: FriendColorStore,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineListUiState())
    val state: StateFlow<MachineListUiState> = mutableState

    private var own: OwnMachines? = null
    private var group: GroupMachines? = null
    private var ownPeaks: List<MachinePeaks> = emptyList()
    private var friendPeaks: List<MachinePeaks> = emptyList()
    private var colors: Map<UserId, Int> = emptyMap()
    private var preferred = PreferredWeightUnit.Kg
    private var loading: Job? = null
    private var loadingFriends: Job? = null
    private val sorting = machineSortChoice(profiles, currentUser, clock, sync, viewModelScope)

    /** The list follows whoever is active, wherever the switch came from. */
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
                ownPeaks = sets.peaks(owner)
                preferred = profiles.preferredUnit(owner)
                sorting.read(owner)
                publish()
                owner?.let(::loadFriends)
            }
    }

    private fun loadFriends(owner: UserId) {
        loadingFriends =
            viewModelScope.launch {
                group = reading { catalogue.group(owner) }.getOrNull()
                publish()
                friendPeaks =
                    reading { friendsRepository.groupPeaks(owner) }.getOrDefault(friendPeaks)
                val offered = group?.offered(own?.machines.orEmpty()).orEmpty()
                colors =
                    reading {
                        friendColors.colorsFor(owner, offered.map { it.owner.userId }.distinct())
                    }.getOrDefault(colors)
                publish()
            }
    }

    fun chooseSort(sort: MachineSort) {
        sorting.choose(sort)
        publish()
    }

    private fun publish() {
        val shown = own?.let { ShownMachines(it, group) }
        val sort = sorting.current
        val now = clock.now()
        val offset = utcOffset.at(now)
        val cards =
            shown?.let {
                MachineCards(
                    it,
                    ownPeaks,
                    friendPeaks,
                    preferred,
                    CalendarDay.of(now, offset),
                    offset,
                )
            }
        val ownOrder = machineOrder(sort, ownPeaks.associateBy { it.machineId })
        val friendOrder = machineOrder(sort, friendPeaks.associateBy { it.machineId })
        mutableState.value =
            MachineListUiState(
                own =
                    cards?.let { c ->
                        shown.own.machines
                            .sortedWith(ownOrder)
                            .map(c::own)
                    },
                friendSections =
                    cards?.friendSections(shown.offered, colors, friendOrder).orEmpty(),
                sort = sort,
            )
    }
}
