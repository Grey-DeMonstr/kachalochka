package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.format.weightCaption

data class MachineListRowUi(
    val id: MachineId,
    val name: String,
    val detail: String,
)

class MachineListViewModel(
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow<List<MachineListRowUi>?>(null)
    val state: StateFlow<List<MachineListRowUi>?> = mutableState

    /** The list follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            mutableState.value =
                machines.all(currentUser.id()).map {
                    MachineListRowUi(it.id, it.name, weightCaption(it))
                }
        }
    }
}
