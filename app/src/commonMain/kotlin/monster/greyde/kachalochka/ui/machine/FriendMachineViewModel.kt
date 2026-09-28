package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.reading
import kotlin.time.Clock

data class FriendMachineUi(
    val name: String,
    val owner: String,
    val note: String,
    val caption: String,
    val platform: String?,
)

/** A group mate's machine, read online, that the account can take as its own linked copy. */
class FriendMachineViewModel(
    private val machineId: MachineId,
    private val owner: UserId,
    private val friends: FriendsRepository,
    private val machines: MachineRepository,
    private val machineLinks: MachineLinkRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FriendMachineUi?>(null)
    val state: StateFlow<FriendMachineUi?> = mutableState
    private val mutableOffline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = mutableOffline
    private val writes = WriteGuard(viewModelScope)

    private var shown: Machine? = null
    private var shownFor: UserId? = null
    private var loading: Job? = null

    init {
        viewModelScope.launch { accounts.activeId.collect { refresh() } }
    }

    /** Cancels the load in flight, so one for a previous account never lands last. */
    fun refresh() {
        loading?.cancel()
        shown = null
        shownFor = null
        mutableState.value = null
        loading =
            viewModelScope.launch {
                val viewer = currentUser.id() ?: return@launch
                reading { friends.groupMachines(viewer).firstOrNull(::isShown) }
                    .onSuccess { found ->
                        shown = found?.machine
                        shownFor = viewer
                        mutableState.value = found?.let(::uiOf)
                        mutableOffline.value = false
                    }.onFailure { mutableOffline.value = true }
            }
    }

    private fun isShown(friend: FriendMachine) =
        friend.machine.id == machineId && friend.owner.userId == owner

    /** Saves the account's copy of the shown machine, linked to it, and hands on the copy. */
    fun take(onTaken: (MachineId) -> Unit) {
        val friend = shown ?: return
        val takenFor = shownFor
        writes.launch {
            val viewer = currentUser.id() ?: return@launch
            if (viewer != takenFor) return@launch
            val (copy, link) = linkedCopy(friend, viewer, clock.now())
            machines.upsert(copy)
            machineLinks.upsert(link)
            onTaken(copy.id)
        }
    }

    private fun uiOf(friend: FriendMachine): FriendMachineUi {
        val machine = friend.machine
        return FriendMachineUi(
            name = machine.name,
            owner = friend.owner.displayName,
            note = machine.setupNote,
            caption = weightCaption(machine),
            platform = platformText(machine),
        )
    }

    private fun platformText(machine: Machine): String? {
        if (machine.platformWeight <= 0) return null
        val added = if (machine.platformIncluded) "прибавляется к записи" else "рядом с названием"
        return "${formatNumber(machine.platformWeight)} ${unitLabel(machine)} · $added"
    }
}
