package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.mergedMachines
import monster.greyde.kachalochka.core.domain.gym.olderMachine
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.reading
import kotlin.time.Clock

data class ChooserRowUi(
    val id: MachineId,
    val name: String,
    val detail: String?,
)

data class LinkChooserUiState(
    val query: String = "",
    val own: List<ChooserRowUi> = emptyList(),
    val friends: List<ChooserRowUi>? = null,
    val merge: MergeUi? = null,
    val error: String? = null,
)

data class MergeUi(
    val title: String,
    val text: String,
)

private const val OFFLINE = "Нет связи с сервером"

/** What [machineId], an own saved machine, can be merged with or linked to. */
class LinkChooserViewModel(
    private val machineId: MachineId,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val machineLinks: MachineLinkRepository,
    private val friends: FriendsRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val sync: SyncTrigger,
    private val profiles: ProfileRepository,
    private val photos: PhotoRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LinkChooserUiState())
    val state: StateFlow<LinkChooserUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var own: List<Machine> = emptyList()
    private var preferred = PreferredWeightUnit.Kg
    private var shownFor: UserId? = null
    private var group: GroupMachines? = null
    private var loading: Job? = null

    /** The merge the dialog asks about: the machine kept, then the one removed. */
    private var pending: Pair<MachineId, MachineId>? = null

    private val offeredFriends: List<FriendMachine>?
        get() {
            val read = group ?: return null
            return read.offered(own)
        }

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    /** Own machines show at once; friends' follow from the network, if it answers. */
    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                val mine = machines.all(owner)
                own = mine
                preferred = profiles.preferredUnit(owner)
                shownFor = owner
                group = null
                publish()
                if (owner == null || mine.none { it.id == machineId }) return@launch
                val found = loadGroupMachines(owner, friends, machineLinks)
                if (currentUser.id() != owner) return@launch
                group = found
                publish()
            }
    }

    fun onQueryChange(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
        publish()
    }

    fun chooseOwn(id: MachineId) {
        val edited = own.firstOrNull { it.id == machineId } ?: return
        val other = own.firstOrNull { it.id == id && id != machineId } ?: return
        viewModelScope.launch {
            val firstSets =
                listOf(edited, other).associate { machine ->
                    machine.id to sets.forMachine(machine.id).minOfOrNull { it.recordedAt }
                }
            val kept = olderMachine(edited, other) { firstSets[it] }
            val removed = if (kept == edited) other else edited
            pending = kept.id to removed.id
            val merge =
                MergeUi(
                    "Объединить тренажёры?",
                    "Останется «${kept.name}», подходы «${removed.name}» перейдут к нему. " +
                        "Это нельзя отменить.",
                )
            mutableState.value = mutableState.value.copy(merge = merge, error = null)
        }
    }

    fun cancelMerge() = closeMerge(error = null)

    private fun closeMerge(error: String?) {
        pending = null
        mutableState.value = mutableState.value.copy(merge = null, error = error)
    }

    /**
     * Friends' links into the removed machine can move only on the server, so a signed-in merge
     * waits for it and writes nothing when it does not answer. Once it has answered, leaving the
     * screen must not stop the local rows halfway.
     */
    fun confirmMerge(onDone: (kept: MachineId) -> Unit) {
        val (keptId, removedId) = pending ?: return
        val mergedFor = shownFor
        writes.launch {
            val owner = currentUser.id()
            val kept = liveOwn(keptId, owner)
            val removed = liveOwn(removedId, owner)
            if (owner != mergedFor || kept == null || removed == null) {
                closeMerge(error = null)
                return@launch
            }
            if (owner != null) {
                val repointed =
                    reading {
                        val intoRemoved =
                            friends.groupLinks(owner).any { it.linkedMachineId == removed.id }
                        if (intoRemoved) friends.repointLinks(removed.id, kept.id)
                    }
                if (repointed.isFailure) {
                    closeMerge(error = OFFLINE)
                    return@launch
                }
            }
            withContext(NonCancellable) {
                val rows =
                    mergedMachines(
                        kept,
                        removed,
                        sets.forMachine(removed.id).filter { it.userId == owner },
                        machineLinks.all(owner),
                        clock.now(),
                        photos.forMachine(removed.id).filter { it.userId == owner },
                    )
                rows.sets.forEach { sets.upsert(it) }
                rows.links.forEach { machineLinks.upsert(it) }
                rows.photos.forEach { photos.upsert(it) }
                machines.upsert(rows.removed)
                sync.request()
            }
            closeMerge(error = null)
            onDone(kept.id)
        }
    }

    private suspend fun liveOwn(
        id: MachineId,
        owner: UserId?,
    ): Machine? = machines.byId(id)?.takeIf { !it.deleted && it.userId == owner }

    fun chooseFriend(
        id: MachineId,
        onDone: () -> Unit,
    ) {
        if (offeredFriends.orEmpty().none { it.machine.id == id }) return
        val offeredTo = shownFor
        writes.launch {
            val owner = currentUser.id() ?: return@launch
            if (owner != offeredTo) return@launch
            liveOwn(machineId, owner) ?: return@launch
            machineLinks.upsert(
                MachineLink(MachineLinkId.random(), owner, machineId, id, clock.now(), false),
            )
            sync.request()
            onDone()
        }
    }

    private fun publish() {
        val needle = mutableState.value.query.trim()

        fun matches(name: String) = name.contains(needle, ignoreCase = true)
        mutableState.value =
            mutableState.value.copy(
                own =
                    own
                        .filter { it.id != machineId && matches(it.name) }
                        .map { ChooserRowUi(it.id, it.name, weightCaption(it, preferred)) },
                friends =
                    offeredFriends
                        ?.filter { matches(it.machine.name) }
                        ?.map {
                            ChooserRowUi(
                                it.machine.id,
                                it.machine.name,
                                friendMachineDetail(it, preferred),
                            )
                        },
            )
    }
}
