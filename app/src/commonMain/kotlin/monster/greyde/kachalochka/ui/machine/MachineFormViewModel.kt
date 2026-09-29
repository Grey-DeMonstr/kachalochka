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
import monster.greyde.kachalochka.core.domain.friends.linkedFriendMachines
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.roundWeight
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.photos.ShownPhoto
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock
import kotlin.time.Instant

data class MachineFormArgs(
    val machineId: MachineId?,
    val copyOf: MachineId?,
    val name: String,
)

data class LinkedMachineUi(
    val machineId: MachineId,
    val ownerId: UserId,
    val label: String,
)

data class LinkingUi(
    val canLink: Boolean = false,
    val linkedWith: List<LinkedMachineUi> = emptyList(),
    val canUnlink: Boolean = false,
    val confirmingUnlink: Boolean = false,
    val error: String? = null,
)

data class MachineFormState(
    val name: String = "",
    val setupNote: String = "",
    val weightMode: WeightMode = WeightMode.Total,
    val platformWeight: String = "0",
    val platformIncluded: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
    val unitLabel: String = "",
    val weightStep: String = "2.5",
    val tags: Set<String> = emptySet(),
    /** Every tag of the account's machines, offered as chips. */
    val knownTags: Set<String> = emptySet(),
    val newTag: String = "",
) {
    val shownTags: List<String>
        get() = (knownTags + tags).sortedBy { it.lowercase() }

    val platformWeightValue: Double?
        get() =
            if (platformWeight.isBlank()) 0.0 else parseDecimal(platformWeight)?.takeIf { it >= 0 }

    val weightStepValue: Double?
        get() = parseDecimal(weightStep)?.let(::roundWeight)?.takeIf { it > 0 }

    val canSave: Boolean
        get() =
            name.isNotBlank() &&
                platformWeightValue != null &&
                weightStepValue != null &&
                (unit != WeightUnit.Custom || unitLabel.isNotBlank())

    companion object {
        const val UNIT_LABEL_LENGTH: Int = 12
        const val TAG_LENGTH: Int = 30

        fun of(
            machine: Machine,
            name: String = machine.name,
        ) = MachineFormState(
            name = name,
            setupNote = machine.setupNote,
            weightMode = machine.weightMode,
            platformWeight = formatNumber(machine.platformWeight),
            platformIncluded = machine.platformIncluded,
            unit = machine.unit,
            unitLabel = machine.unitLabel,
            weightStep = formatNumber(machine.weightStep),
            tags = machine.tags,
        )
    }
}

/** A photo taken in the form, written with the machine when it is saved. */
private class TakenPhoto(
    val id: PhotoId,
    val jpeg: ByteArray,
    val takenAt: Instant,
)

class MachineFormViewModel(
    private val args: MachineFormArgs,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val friends: FriendsRepository,
    private val machineLinks: MachineLinkRepository,
    private val sync: SyncTrigger,
    private val photoRows: PhotoRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineFormState(name = args.name))
    val state: StateFlow<MachineFormState> = mutableState
    private val mutableLinking = MutableStateFlow(LinkingUi())
    val linking: StateFlow<LinkingUi> = mutableLinking
    private val writes = WriteGuard(viewModelScope)

    /** The saved machine's photos the form still shows, then the ones taken since. */
    private var savedPhotos: List<Photo> = emptyList()
    private var removedPhotos: List<Photo> = emptyList()
    private var takenPhotos: List<TakenPhoto> = emptyList()
    private val mutablePhotos = MutableStateFlow<List<ShownPhoto>>(emptyList())
    val photos: StateFlow<List<ShownPhoto>> = mutablePhotos

    private var existing: Machine? = null
    private var loaded = false
    private var readingLinks: Job? = null

    /** A switch keeps the typed edits and drops the row under them, which it no longer owns. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect { active ->
                existing = existing?.takeIf { it.userId == active }
                if (existing == null) {
                    savedPhotos = emptyList()
                    removedPhotos = emptyList()
                    showPhotos()
                }
                refreshLinks()
            }
        }
    }

    /**
     * The screen asks again whenever it re-enters composition: the form keeps its edits then, and
     * the links are read again, since the chooser may have changed them.
     */
    fun load() {
        if (loaded) {
            refreshLinks()
            return
        }
        loaded = true
        viewModelScope.launch {
            val current = args.machineId?.let { machines.byId(it) }
            existing = current
            val source = current ?: args.copyOf?.let { machines.byId(it) }
            val shown =
                when {
                    current != null -> MachineFormState.of(current)
                    source != null -> MachineFormState.of(source, name = args.name)
                    else -> MachineFormState(name = args.name)
                }
            val known = machines.all(currentUser.id()).flatMap { it.tags }.toSet()
            mutableState.value = shown.copy(knownTags = known)
            savedPhotos = current?.let { photoRows.forMachine(it.id) }.orEmpty()
            showPhotos()
            refreshLinks()
        }
    }

    private fun refreshLinks() {
        readingLinks?.cancel()
        readingLinks = viewModelScope.launch { readLinks() }
    }

    /**
     * Own links are on hand offline; friends' links and machines need the network, and without
     * it the form names no friends' machines.
     */
    private suspend fun readLinks() {
        val shown = existing
        val owner = accounts.activeId.value
        if (shown == null || shown.userId != owner) {
            mutableLinking.value = LinkingUi(error = mutableLinking.value.error)
            return
        }
        val ownLinks = machineLinks.all(owner)
        val ownTouch = ownLinks.any { it.touches(shown.id) }
        mutableLinking.value =
            mutableLinking.value.let {
                it.copy(
                    canLink = true,
                    canUnlink = it.canUnlink || ownTouch,
                )
            }
        val group = owner?.let { loadGroupMachines(it, friends, machineLinks) }
        if (accounts.activeId.value != owner || existing?.id != shown.id) return
        val linked = group?.let { linkedFriendMachines(shown.id, it.friends, it.clusters) }
        mutableLinking.value =
            mutableLinking.value.copy(
                linkedWith =
                    linked.orEmpty().map {
                        LinkedMachineUi(
                            it.machine.id,
                            it.owner.userId,
                            "${it.machine.name} (${it.owner.displayName})",
                        )
                    },
                canUnlink = (group?.links ?: ownLinks).any { it.touches(shown.id) },
            )
    }

    private fun MachineLink.touches(machine: MachineId) =
        machineId == machine || linkedMachineId == machine

    fun askToUnlink() {
        if (mutableLinking.value.canUnlink) {
            mutableLinking.value = mutableLinking.value.copy(confirmingUnlink = true, error = null)
        }
    }

    fun cancelUnlink() {
        mutableLinking.value = mutableLinking.value.copy(confirmingUnlink = false)
    }

    /**
     * Friends' links into the machine can be broken only on the server, so they go first and
     * nothing is written when it does not answer.
     */
    fun confirmUnlink() {
        val shown = existing ?: return
        writes.launch {
            val owner = currentUser.id()
            machines.byId(shown.id)?.takeIf { it.userId == owner } ?: return@launch
            val broken = owner?.let { reading { friends.breakLinks(shown.id) } }
            if (broken?.isFailure == true) {
                mutableLinking.value =
                    mutableLinking.value.copy(
                        confirmingUnlink = false,
                        error = AppStrings.current.offline,
                    )
                return@launch
            }
            val now = clock.now()
            machineLinks
                .all(owner)
                .filter { it.touches(shown.id) }
                .forEach { machineLinks.upsert(it.copy(deleted = true, updatedAt = now)) }
            sync.request()
            mutableLinking.value =
                mutableLinking.value.copy(confirmingUnlink = false, error = null)
            refreshLinks()
        }
    }

    fun addPhoto(jpeg: ByteArray) {
        takenPhotos = takenPhotos + TakenPhoto(PhotoId.random(), jpeg, clock.now())
        showPhotos()
    }

    fun removePhoto(key: String) {
        val saved = savedPhotos.firstOrNull { it.id.value == key }
        if (saved != null) {
            savedPhotos = savedPhotos - saved
            removedPhotos = removedPhotos + saved
        }
        takenPhotos = takenPhotos.filterNot { it.id.value == key }
        showPhotos()
    }

    private fun showPhotos() {
        mutablePhotos.value =
            savedPhotos.map { ShownPhoto(it.id.value, it) } +
            takenPhotos.map { ShownPhoto(it.id.value, it.jpeg) }
    }

    fun update(change: (MachineFormState) -> MachineFormState) {
        mutableState.value = change(mutableState.value)
    }

    fun toggleTag(tag: String) =
        update { it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag) }

    fun typeNewTag(text: String) =
        update { it.copy(newTag = text.take(MachineFormState.TAG_LENGTH)) }

    fun addNewTag() =
        update { form ->
            val tag = form.newTag.trim()
            if (tag.isEmpty()) {
                form.copy(
                    newTag = "",
                )
            } else {
                form.copy(tags = form.tags + tag, newTag = "")
            }
        }

    fun save(onSaved: (MachineId) -> Unit) {
        val form = mutableState.value
        val platformWeight = form.platformWeightValue ?: return
        val weightStep = form.weightStepValue ?: return
        if (!form.canSave) return
        writes.launch {
            val now = clock.now()
            val owner = currentUser.id()
            val base =
                existing?.takeIf { it.userId == owner }
                    ?: Machine.new(form.name.trim(), owner, now)
            val machine =
                base.copy(
                    name = form.name.trim(),
                    setupNote = form.setupNote.trim(),
                    weightMode = form.weightMode,
                    platformWeight = platformWeight,
                    platformIncluded = form.platformIncluded,
                    unit = form.unit,
                    unitLabel = if (form.unit == WeightUnit.Custom) form.unitLabel.trim() else "",
                    weightStep = weightStep,
                    tags = form.tags,
                    updatedAt = now,
                )
            machines.upsert(machine)
            takenPhotos.forEach {
                photoRows.add(Photo(it.id, owner, machine.id, it.takenAt, now, false), it.jpeg)
            }
            removedPhotos
                .filter { it.userId == owner }
                .forEach { photoRows.upsert(it.copy(deleted = true, updatedAt = now)) }
            onSaved(machine.id)
        }
    }
}
