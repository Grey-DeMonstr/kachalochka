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
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.shownLabel
import monster.greyde.kachalochka.ui.format.shownWeight
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.photos.ShownPhoto
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

data class FriendMachineUi(
    val name: String,
    val owner: String,
    val note: String,
    val caption: String,
    val platform: String?,
    val canTake: Boolean,
    val photos: List<ShownPhoto> = emptyList(),
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
    private val sync: SyncTrigger,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FriendMachineUi?>(null)
    val state: StateFlow<FriendMachineUi?> = mutableState
    private val mutableOffline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = mutableOffline
    private val writes = WriteGuard(viewModelScope)

    private var shown: Machine? = null
    private var shownFor: UserId? = null
    private var loading: Job? = null

    private var spokenIn = AppStrings.current

    /** Reads again when the language changed while another screen was open. */
    fun speak() {
        if (AppStrings.current == spokenIn) return
        spokenIn = AppStrings.current
        refresh()
    }

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
                reading {
                    val found = friends.groupMachines(viewer).firstOrNull(::isShown)
                    val had = alreadyHas(viewer)
                    val ui =
                        found?.let {
                            val photos = friends.photos(machineId)
                            uiOf(it, profiles.preferredUnit(viewer), canTake = !had, photos)
                        }
                    Triple(found, had, ui)
                }.onSuccess { (found, had, ui) ->
                    shown = found?.machine?.takeUnless { had }
                    shownFor = viewer
                    mutableState.value = ui
                    mutableOffline.value = false
                }.onFailure { mutableOffline.value = true }
            }
    }

    private fun isShown(friend: FriendMachine) =
        friend.machine.id == machineId && friend.owner.userId == owner

    /** Another copy would duplicate an own machine already linked to this one. */
    private suspend fun alreadyHas(viewer: UserId): Boolean {
        val clusters = MachineClusters(visibleLinks(viewer, friends, machineLinks))
        val own = machines.all(viewer).map { it.id }.toSet()
        return clusters.of(machineId).any { it in own }
    }

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
            sync.request()
            onTaken(copy.id)
        }
    }

    private fun uiOf(
        friend: FriendMachine,
        preferred: PreferredWeightUnit,
        canTake: Boolean,
        photos: List<Photo>,
    ): FriendMachineUi {
        val machine = friend.machine
        return FriendMachineUi(
            name = machine.name,
            owner = friend.owner.displayName,
            note = machine.setupNote,
            caption = weightCaption(machine, preferred),
            platform = platformText(machine, preferred),
            canTake = canTake,
            photos = photos.map { ShownPhoto(it.id.value, it) },
        )
    }

    private fun platformText(
        machine: Machine,
        preferred: PreferredWeightUnit,
    ): String? {
        if (machine.platformWeight <= 0) return null
        val strings = AppStrings.current
        val added = if (machine.platformIncluded) strings.platformAdded else strings.platformBeside
        val weight = formatNumber(shownWeight(machine.platformWeight, machine, preferred))
        return "$weight ${shownLabel(machine, preferred)} · $added"
    }
}
