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
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.coverPhoto
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.preferredUnit
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
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
    private val friends: FriendsRepository,
    private val machineLinks: MachineLinkRepository,
    private val profiles: ProfileRepository,
    private val photos: PhotoRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineListUiState())
    val state: StateFlow<MachineListUiState> = mutableState

    private var own: List<Machine>? = null
    private var ownPhotos: List<Photo> = emptyList()
    private var ownLinks: List<MachineLink> = emptyList()
    private var groupPhotos: List<Photo> = emptyList()
    private var preferred = PreferredWeightUnit.Kg
    private var shownFor: UserId? = null
    private var group: GroupMachines? = null
    private var friendsFor: UserId? = null
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
                own = machines.all(owner)
                ownPhotos = photos.all(owner)
                ownLinks = machineLinks.all(owner)
                preferred = profiles.preferredUnit(owner)
                shownFor = owner
                publish()
                owner?.let(::loadFriends)
            }
    }

    private fun loadFriends(owner: UserId) {
        loadingFriends =
            viewModelScope.launch {
                val found = loadGroupMachines(owner, friends, machineLinks)
                val mates = found?.let { reading { friends.groupPhotos(owner) }.getOrNull() }
                if (currentUser.id() != owner) return@launch
                group = found
                groupPhotos = mates.orEmpty()
                friendsFor = owner
                publish()
            }
    }

    private fun publish() {
        val mine = own
        // Friends read for any account but the one shown are never listed.
        val read = group?.takeIf { friendsFor != null && friendsFor == shownFor }
        val clusters = read?.clusters ?: MachineClusters(ownLinks)
        val shownPhotos = ownPhotos + if (read != null) groupPhotos else emptyList()
        mutableState.value =
            MachineListUiState(
                own =
                    mine?.map {
                        MachineListRowUi(
                            it.id,
                            it.name,
                            weightCaption(it, preferred),
                            photo = coverPhoto(it.id, shownPhotos, clusters),
                        )
                    },
                friends =
                    read?.offered(mine.orEmpty()).orEmpty().map {
                        MachineListRowUi(
                            it.machine.id,
                            it.machine.name,
                            friendMachineDetail(it, preferred),
                            it.owner.userId,
                            coverPhoto(it.machine.id, shownPhotos, clusters),
                        )
                    },
            )
    }
}
