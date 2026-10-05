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
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.photos.ShownPhoto
import monster.greyde.kachalochka.ui.stats.MachineStatsUi
import monster.greyde.kachalochka.ui.stats.machineStatsUi
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

/**
 * [stats] are the friend's own results on the machine over [period], in the viewer's unit; the
 * settings, [caption] and [platform], stay in the machine's own unit.
 */
data class FriendMachineUi(
    val name: String,
    val owner: String,
    val note: String,
    val caption: String,
    val platform: String?,
    val canTake: Boolean,
    val photos: List<ShownPhoto>,
    val tags: List<String>,
    val period: StatsPeriod,
    val stats: MachineStatsUi,
)

/** A group mate's machine, read online, that the account can take as its own linked copy. */
class FriendMachineViewModel(
    private val machineId: MachineId,
    private val owner: UserId,
    private val friends: FriendsRepository,
    private val catalogue: MachineCatalogue,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val profiles: ProfileRepository,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FriendMachineUi?>(null)
    val state: StateFlow<FriendMachineUi?> = mutableState
    private val mutableOffline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = mutableOffline
    private val writes = WriteGuard(viewModelScope)

    /** What the last read brought, kept so a period change redraws without the network. */
    private class Read(
        val friend: FriendMachine,
        val had: Boolean,
        val photos: List<Photo>,
        val sets: List<WorkoutSet>,
        val preferred: PreferredWeightUnit,
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

    /** Cancels the load in flight, so one for a previous account never lands last. */
    fun refresh() {
        loading?.cancel()
        read = null
        shownFor = null
        mutableState.value = null
        loading =
            viewModelScope.launch {
                val viewer = currentUser.id() ?: return@launch
                reading {
                    friends.groupMachines(viewer).firstOrNull(::isShown)?.let { found ->
                        Read(
                            found,
                            alreadyHas(viewer),
                            friends.photos(machineId),
                            friends.setsOn(owner, machineId),
                            profiles.preferredUnit(viewer),
                        )
                    }
                }.onSuccess { found ->
                    read = found
                    shownFor = viewer
                    mutableState.value = found?.let(::uiOf)
                    mutableOffline.value = false
                }.onFailure { mutableOffline.value = true }
            }
    }

    fun choosePeriod(chosen: StatsPeriod) {
        period = chosen
        read?.let { mutableState.value = uiOf(it) }
    }

    private fun isShown(friend: FriendMachine) =
        friend.machine.id == machineId && friend.owner.userId == owner

    /** Another copy would duplicate an own machine already linked to this one. */
    private suspend fun alreadyHas(viewer: UserId): Boolean {
        val clusters = catalogue.clusters(viewer)
        val own =
            catalogue
                .own(viewer)
                .machines
                .map { it.id }
                .toSet()
        return clusters.of(machineId).any { it in own }
    }

    /** Saves the account's copy of the shown machine, linked to it, and hands on the copy. */
    fun take(onTaken: (MachineId) -> Unit) {
        val friend = read?.takeUnless { it.had }?.friend?.machine ?: return
        val takenFor = shownFor
        writes.launch {
            val viewer = currentUser.id() ?: return@launch
            if (viewer != takenFor) return@launch
            onTaken(catalogue.take(viewer, friend).id)
        }
    }

    private fun uiOf(read: Read): FriendMachineUi {
        val machine = read.friend.machine
        val now = clock.now()
        val today = CalendarDay.of(now, utcOffset.at(now))
        val cover =
            read.photos.firstOrNull { it.id == machine.coverPhoto } ?: read.photos.firstOrNull()
        return FriendMachineUi(
            name = machine.name,
            owner = read.friend.owner.displayName,
            note = machine.setupNote,
            caption = weightCaption(machine, PreferredWeightUnit.Mixed),
            platform = platformText(machine),
            canTake = !read.had,
            photos = read.photos.map { ShownPhoto(it.id.value, it, cover = it == cover) },
            tags = machine.tags.sortedBy { it.lowercase() },
            period = period,
            stats = machineStatsUi(machine, read.sets, period, today, read.preferred, utcOffset),
        )
    }

    private fun platformText(machine: Machine): String? {
        if (machine.platformWeight <= 0) return null
        val strings = AppStrings.current
        val added = if (machine.platformIncluded) strings.platformAdded else strings.platformBeside
        return "${formatNumber(machine.platformWeight)} ${unitLabel(machine)} · $added"
    }
}
