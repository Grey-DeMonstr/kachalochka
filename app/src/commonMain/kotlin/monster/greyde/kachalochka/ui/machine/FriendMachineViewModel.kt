package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.photoOrder
import monster.greyde.kachalochka.core.domain.gym.sortedTags
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.DeviceClock
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.photos.ShownPhoto
import monster.greyde.kachalochka.ui.stats.MachineStatsUi
import monster.greyde.kachalochka.ui.stats.machineStatsUi
import monster.greyde.kachalochka.ui.strings.AppStrings

/**
 * [stats] are the friend's own results on the machine over [period], in the viewer's unit, null
 * until they are read; the settings, [caption] and [platform], stay in the machine's own unit.
 */
data class FriendMachineUi(
    val name: String,
    val owner: String,
    val note: String,
    val caption: String,
    val platform: String?,
    /** Neither taking it nor linking an own machine to it would duplicate one. */
    val canTake: Boolean,
    val linkedWith: List<LinkedMachineUi>,
    val photos: List<ShownPhoto>,
    val tags: List<String>,
    val period: StatsPeriod,
    val stats: MachineStatsUi?,
)

/** A group mate's machine, read online, that the account can take as its own linked copy. */
class FriendMachineViewModel(
    private val machineId: MachineId,
    private val owner: UserId,
    private val friends: FriendsRepository,
    private val catalogue: MachineCatalogue,
    private val reads: MachineReads,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val profiles: ProfileRepository,
    private val clock: DeviceClock,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FriendMachineUi?>(null)
    val state: StateFlow<FriendMachineUi?> = mutableState
    private val mutableOffline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = mutableOffline
    private val writes = WriteGuard(viewModelScope)

    /** What the last read brought, kept so a period change redraws without the network. */
    private data class Read(
        val friend: FriendMachine,
        val group: GroupMachines,
        /** Another copy or link would duplicate an own machine already linked to this one. */
        val had: Boolean,
        val linked: List<LinkedMachineUi>,
        val photos: List<Photo>,
        val preferred: PreferredWeightUnit,
        val sets: List<WorkoutSet>? = null,
    )

    private var read: Read? = null
    private var shownFor: UserId? = null
    private var period = StatsPeriod.Month
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

    /**
     * Cancels the load in flight, so one for a previous account never lands last. The friend's
     * sets are read beside the group; the last group read shows the machine before either lands.
     * A server that does not answer shows no machine, not the cached one.
     */
    fun refresh() {
        loading?.cancel()
        forgetShown()
        loading =
            viewModelScope.launch {
                val viewer = currentUser.id() ?: return@launch
                reading {
                    coroutineScope {
                        val sets = async { friends.setsOn(owner, machineId) }
                        val preferred = profiles.preferredUnit(viewer)
                        var serverDown = false
                        reads.machines(viewer).collect { read ->
                            val group = read.group ?: return@collect
                            if (read.offline) {
                                forgetShown()
                                sets.cancel()
                                serverDown = true
                                return@collect
                            }
                            val found = readOf(read, preferred)
                            when {
                                !group.fresh -> found?.let { show(viewer, it) }
                                found == null -> {
                                    forgetShown()
                                    sets.cancel()
                                }
                                else -> {
                                    show(viewer, found)
                                    show(viewer, found.copy(sets = sets.await()))
                                }
                            }
                        }
                        serverDown
                    }
                }.onSuccess { serverDown ->
                    mutableOffline.value = serverDown
                }.onFailure {
                    forgetShown()
                    mutableOffline.value = true
                }
            }
    }

    private fun forgetShown() {
        read = null
        shownFor = null
        mutableState.value = null
    }

    private fun readOf(
        read: MachineRead,
        preferred: PreferredWeightUnit,
    ): Read? {
        val group = read.group?.machines ?: return null
        val found = group.friends.firstOrNull(::isShown) ?: return null
        val shown = read.shown
        return Read(
            found,
            group,
            shown.own.machines.any { shown.clusters.sameMachine(it.id, machineId) },
            shown.linkedWith(machineId),
            group.photos.filter { it.machineId == machineId }.sortedWith(photoOrder),
            preferred,
        )
    }

    private fun show(
        viewer: UserId,
        shown: Read,
    ) {
        read = shown
        shownFor = viewer
        mutableState.value = uiOf(shown)
    }

    /** The shown machine with the group read it came from, to place it elsewhere. */
    fun placed(): PlacedMachine = PlacedMachine(machineId, ofFriend = true, group = read?.group)

    fun choosePeriod(chosen: StatsPeriod) {
        period = chosen
        read?.let { mutableState.value = uiOf(it) }
    }

    private fun isShown(friend: FriendMachine) =
        friend.machine.id == machineId && friend.owner.userId == owner

    /** Saves the account's copy of the shown machine, linked to it, and hands on the copy. */
    fun take(onTaken: (MachineId) -> Unit) {
        val shown = read?.takeUnless { it.had } ?: return
        val takenFor = shownFor
        writes.launch {
            val viewer = currentUser.id() ?: return@launch
            if (viewer != takenFor) return@launch
            onTaken(catalogue.take(viewer, shown.friend.machine, shown.group).id)
        }
    }

    private fun uiOf(read: Read): FriendMachineUi {
        val machine = read.friend.machine
        val today = clock.today()
        val cover =
            read.photos.firstOrNull { it.id == machine.coverPhoto } ?: read.photos.firstOrNull()
        return FriendMachineUi(
            name = machine.name,
            owner = read.friend.owner.displayName,
            note = machine.setupNote,
            caption = weightCaption(machine, PreferredWeightUnit.Mixed),
            platform = platformText(machine),
            canTake = !read.had,
            linkedWith = read.linked,
            photos = read.photos.map { ShownPhoto(it.id.value, it, cover = it == cover) },
            tags = sortedTags(machine.tags),
            period = period,
            stats =
                read.sets?.let {
                    machineStatsUi(
                        machine,
                        it,
                        period,
                        today,
                        read.preferred,
                        UtcOffset(clock::offsetAt),
                    )
                },
        )
    }

    private fun platformText(machine: Machine): String? {
        if (machine.platformWeight <= 0) return null
        val strings = AppStrings.current
        val added = if (machine.platformIncluded) strings.platformAdded else strings.platformBeside
        return "${formatNumber(machine.platformWeight)} ${unitLabel(machine)} · $added"
    }
}
