package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.friendMachineDetail
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.reading

data class MachineListUiState(
    val own: List<MachineListRowUi>? = null,
    val friends: List<MachineListRowUi> = emptyList(),
)

data class MachineListRowUi(
    val id: MachineId,
    val name: String,
    val detail: String,
    /** The group mate owning a friends' row; null on the account's own. */
    val friend: UserId? = null,
    val photo: Photo? = null,
)

class MachineListViewModel(
    private val catalogue: MachineCatalogue,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineListUiState())
    val state: StateFlow<MachineListUiState> = mutableState

    private var own: OwnMachines? = null
    private var group: GroupMachines? = null
    private var preferred = PreferredWeightUnit.Kg
    private var loading: Job? = null
    private var loadingFriends: Job? = null

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
                preferred = profiles.preferredUnit(owner)
                publish()
                owner?.let(::loadFriends)
            }
    }

    private fun loadFriends(owner: UserId) {
        loadingFriends =
            viewModelScope.launch {
                group = reading { catalogue.group(owner) }.getOrNull()
                publish()
            }
    }

    private fun publish() {
        val shown = own?.let { ShownMachines(it, group) }
        mutableState.value =
            MachineListUiState(
                own =
                    shown?.let { s ->
                        s.own.machines.map {
                            MachineListRowUi(
                                it.id,
                                it.name,
                                weightCaption(it, preferred),
                                photo = s.cover(it.id),
                            )
                        }
                    },
                friends =
                    shown
                        ?.let { s ->
                            s.offered.map {
                                MachineListRowUi(
                                    it.machine.id,
                                    it.machine.name,
                                    friendMachineDetail(it, preferred),
                                    it.owner.userId,
                                    s.cover(it.machine.id),
                                )
                            }
                        }.orEmpty(),
            )
    }
}
