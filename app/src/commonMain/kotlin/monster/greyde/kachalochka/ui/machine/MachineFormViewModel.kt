package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
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
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.convertedSets
import monster.greyde.kachalochka.core.domain.gym.convertedWeight
import monster.greyde.kachalochka.core.domain.gym.convertible
import monster.greyde.kachalochka.core.domain.gym.guessedLbStep
import monster.greyde.kachalochka.core.domain.gym.photoOrder
import monster.greyde.kachalochka.core.domain.gym.platformShift
import monster.greyde.kachalochka.core.domain.gym.roundWeight
import monster.greyde.kachalochka.core.domain.gym.shiftedSets
import monster.greyde.kachalochka.core.domain.gym.sideFactor
import monster.greyde.kachalochka.core.domain.gym.sidedSets
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.format.weightShift
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.photos.ShownPhoto
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock
import kotlin.time.Instant

data class MachineFormArgs(
    val machineId: MachineId?,
    val copyOf: MachineId?,
    val name: String,
    /** The tags a new machine starts with. */
    val tags: List<String> = emptyList(),
)

/** A tag of a friend's machine the account has not got, offered with the friend it came from. */
data class FriendTagUi(
    val tag: String,
    val friend: Friend,
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
    /** Friends' tags, offered until one is chosen and so becomes the account's own. */
    val friendTags: List<FriendTagUi> = emptyList(),
) {
    val shownTags: List<String>
        get() = (knownTags + tags).sortedBy { it.lowercase() }

    val offeredFriendTags: List<FriendTagUi>
        get() = friendTags.filter { offered -> shownTags.none { it.equals(offered.tag, true) } }

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

data class RecalculationUi(
    val text: String,
)

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
    private val catalogue: MachineCatalogue,
    private val sets: WorkoutSetRepository,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(MachineFormState(name = args.name, tags = args.tags.toSet()))
    val state: StateFlow<MachineFormState> = mutableState
    private val mutableLinking = MutableStateFlow(LinkingUi())
    val linking: StateFlow<LinkingUi> = mutableLinking
    private val mutableRecalculation = MutableStateFlow<RecalculationUi?>(null)
    val recalculation: StateFlow<RecalculationUi?> = mutableRecalculation
    private val writes = WriteGuard(viewModelScope)
    private var pendingSave: Pair<MachineFormState, (MachineId) -> Unit>? = null

    /** The saved machine's photos the form still shows, then the ones taken since. */
    private var savedPhotos: List<Photo> = emptyList()
    private var removedPhotos: List<Photo> = emptyList()
    private var takenPhotos: List<TakenPhoto> = emptyList()

    /** Photos of the friends' machines in this one's cluster, read online. */
    private var friendPhotos: List<ShownPhoto> = emptyList()
    private var coverChoice: PhotoId? = null
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
                    friendPhotos = emptyList()
                    coverChoice = null
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
                    else -> MachineFormState(name = args.name, tags = args.tags.toSet())
                }
            val owner = currentUser.id()
            val known = machines.all(owner).flatMap { it.tags }.toSet()
            mutableState.value = shown.copy(knownTags = known)
            savedPhotos = current?.let { photoRows.forMachine(it.id) }.orEmpty()
            coverChoice = current?.coverPhoto
            showPhotos()
            refreshLinks()
            owner?.let { readFriendTags(it) }
        }
    }

    /**
     * Takes a friend's machine's name, tags and how it counts its weight into the form, as typed
     * edits: saving then asks about recorded sets as any other edit does. A tag the form has in
     * another letter case is not added again.
     */
    fun copySettings(friendMachine: MachineId) {
        viewModelScope.launch {
            val owner = currentUser.id() ?: return@launch
            val group = reading { catalogue.group(owner) }.getOrNull() ?: return@launch
            if (accounts.activeId.value != owner) return@launch
            val source = group.friends.firstOrNull { it.machine.id == friendMachine }?.machine
            source ?: return@launch
            update { form ->
                val tags =
                    source.tags.map { tag ->
                        form.shownTags.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag
                    }
                form.copy(
                    name = source.name,
                    tags = form.tags + tags,
                    weightMode = source.weightMode,
                    platformWeight = formatNumber(source.platformWeight),
                    platformIncluded = source.platformIncluded,
                    unit = source.unit,
                    unitLabel = source.unitLabel,
                    weightStep = formatNumber(source.weightStep),
                )
            }
        }
    }

    /** Each friend's tag once, from the first friend by name who has it. */
    private suspend fun readFriendTags(owner: UserId) {
        val group = reading { catalogue.group(owner) }.getOrNull() ?: return
        if (accounts.activeId.value != owner) return
        val offered =
            group.friends
                .sortedBy { it.owner.displayName.lowercase() }
                .flatMap { mate -> mate.machine.tags.map { FriendTagUi(it, mate.owner) } }
                .distinctBy { it.tag.lowercase() }
                .sortedBy { it.tag.lowercase() }
        update { it.copy(friendTags = offered) }
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
        val group = owner?.let { reading { catalogue.group(it) }.getOrNull() }
        val own = catalogue.own(owner)
        if (accounts.activeId.value != owner || existing?.id != shown.id) return
        if (group != null) {
            friendPhotos = friendPhotosOf(shown.id, group)
            showPhotos()
        }
        mutableLinking.value =
            mutableLinking.value.copy(
                linkedWith = group?.let { ShownMachines(own, it).linkedWith(shown.id) }.orEmpty(),
                canUnlink = (group?.links ?: ownLinks).any { it.touches(shown.id) },
            )
    }

    private fun friendPhotosOf(
        machine: MachineId,
        group: GroupMachines,
    ): List<ShownPhoto> {
        val owners = group.friends.associate { it.machine.id to it.owner }
        val cluster = group.clusters.of(machine)
        return group.photos
            .filter { !it.deleted && it.machineId != machine && it.machineId in cluster }
            .sortedWith(photoOrder)
            .mapNotNull { photo ->
                owners[photo.machineId]?.let { ShownPhoto(photo.id.value, photo, owner = it) }
            }
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

    /** The photo standing for the machine in every list, written on save. */
    fun makeCover(key: String) {
        coverChoice = PhotoId(key)
        showPhotos()
    }

    fun removePhoto(key: String) {
        if (coverChoice?.value == key) coverChoice = null
        val saved = savedPhotos.firstOrNull { it.id.value == key }
        if (saved != null) {
            savedPhotos = savedPhotos - saved
            removedPhotos = removedPhotos + saved
        }
        takenPhotos = takenPhotos.filterNot { it.id.value == key }
        showPhotos()
    }

    /** As `coverPhoto` picks: the chosen photo while shown, else the first own, else a friend's. */
    private fun showPhotos() {
        val shown =
            savedPhotos.map { ShownPhoto(it.id.value, it) } +
                takenPhotos.map { ShownPhoto(it.id.value, it.jpeg) } +
                friendPhotos
        val cover =
            coverChoice?.value?.takeIf { chosen -> shown.any { it.key == chosen } }
                ?: shown.firstOrNull()?.key
        mutablePhotos.value = shown.map { it.copy(cover = it.key == cover) }
    }

    fun update(change: (MachineFormState) -> MachineFormState) {
        mutableState.value = change(mutableState.value)
    }

    /**
     * Between kilograms and pounds the platform and the step convert at once. A pound step is
     * guessed from the sets recorded in kilograms, which were read off the pound stack.
     */
    fun chooseUnit(unit: WeightUnit) {
        val from = mutableState.value.unit
        if (!convertible(from, unit)) {
            update { it.copy(unit = unit) }
            return
        }
        viewModelScope.launch {
            val saved = existing?.takeIf { it.unit == from }
            val recorded = saved?.let { recordedSets(it, currentUser.id()) }.orEmpty()
            update { form ->
                if (form.unit != from) return@update form
                val step =
                    form.weightStepValue?.let {
                        if (unit == WeightUnit.Lb) {
                            guessedLbStep(recorded.map { set -> set.weight }, it)
                        } else {
                            convertedWeight(it, from, unit, lbStep = it).coerceAtLeast(0.1)
                        }
                    }
                val platform =
                    form.platformWeightValue?.let {
                        convertedWeight(it, from, unit, lbStep = step ?: 1.25)
                    }
                form.copy(
                    unit = unit,
                    weightStep = step?.let(::formatNumber) ?: form.weightStep,
                    platformWeight = platform?.let(::formatNumber) ?: form.platformWeight,
                )
            }
        }
    }

    fun toggleTag(tag: String) =
        update { it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag) }

    fun typeNewTag(text: String) =
        update { it.copy(newTag = text.take(MachineFormState.TAG_LENGTH)) }

    /** A tag typed in another case picks the existing one, so a visit never splits over it. */
    fun addNewTag() =
        update { form ->
            val typed = form.newTag.trim().replace(Regex("""\s+"""), " ")
            val tag = form.shownTags.firstOrNull { it.equals(typed, ignoreCase = true) } ?: typed
            val tags = if (tag.isEmpty()) form.tags else form.tags + tag
            form.copy(tags = tags, newTag = "")
        }

    /**
     * A platform, unit or side change over recorded sets waits for [recalculate] or
     * [keepRecorded], since only the user knows how the recorded weights were read.
     */
    fun save(onSaved: (MachineId) -> Unit) {
        val form = mutableState.value
        if (form.platformWeightValue == null || form.weightStepValue == null) return
        if (!form.canSave) return
        writes.launch {
            val owner = currentUser.id()
            val (base, machine) = edited(form, owner)
            val shift = platformShift(base, machine)
            val converted = convertible(base.unit, machine.unit)
            val factor = sideFactor(base.weightMode, machine.weightMode)
            val recorded =
                if (shift == 0.0 && !converted && factor == 1.0) {
                    emptyList()
                } else {
                    recordedSets(base, owner)
                }
            if (recorded.isEmpty()) {
                write(form, onSaved, recalculate = false)
                return@launch
            }
            pendingSave = form to onSaved
            val strings = AppStrings.current
            val sets = setCount(recorded.size)
            val shiftText = weightShift(shift, machine).takeIf { shift != 0.0 }
            val sideText =
                when {
                    factor < 1.0 -> strings.recalculatePerSideText(sets)
                    factor > 1.0 -> strings.recalculateTotalText(sets)
                    else -> null
                }
            val weightText =
                when {
                    converted ->
                        strings.recalculateUnitText(
                            sets,
                            unitLabel(base),
                            unitLabel(machine),
                            shiftText,
                        )
                    shiftText != null -> strings.recalculateText(sets, shiftText)
                    else -> null
                }
            mutableRecalculation.value =
                RecalculationUi(listOfNotNull(sideText, weightText).joinToString("\n\n"))
        }
    }

    fun recalculate() = finishSave(recalculate = true)

    fun keepRecorded() = finishSave(recalculate = false)

    fun dismissRecalculation() {
        pendingSave = null
        mutableRecalculation.value = null
    }

    private fun finishSave(recalculate: Boolean) {
        val (form, onSaved) = pendingSave ?: return
        dismissRecalculation()
        writes.launch { write(form, onSaved, recalculate) }
    }

    private suspend fun recordedSets(
        machine: Machine,
        owner: UserId?,
    ): List<WorkoutSet> =
        if (machine.id != existing?.id) {
            emptyList()
        } else {
            sets.forMachine(machine.id).filter { !it.deleted && it.userId == owner }
        }

    /** The machine the form edits as stored, then with the form's values. */
    private fun edited(
        form: MachineFormState,
        owner: UserId?,
    ): Pair<Machine, Machine> {
        val now = clock.now()
        val base =
            existing?.takeIf { it.userId == owner }
                ?: Machine.new(form.name.trim(), owner, now)
        val machine =
            base.copy(
                name = form.name.trim(),
                setupNote = form.setupNote.trim(),
                weightMode = form.weightMode,
                platformWeight = form.platformWeightValue ?: base.platformWeight,
                platformIncluded = form.platformIncluded,
                unit = form.unit,
                unitLabel = if (form.unit == WeightUnit.Custom) form.unitLabel.trim() else "",
                weightStep = form.weightStepValue ?: base.weightStep,
                tags = form.tags,
                coverPhoto = coverChoice,
                updatedAt = now,
            )
        return base to machine
    }

    private suspend fun write(
        form: MachineFormState,
        onSaved: (MachineId) -> Unit,
        recalculate: Boolean,
    ) {
        val owner = currentUser.id()
        val (base, machine) = edited(form, owner)
        val now = machine.updatedAt
        machines.upsert(machine)
        if (recalculate) {
            val recorded = recordedSets(base, owner)
            val converted =
                convertedSets(recorded, base.unit, machine.unit, machine.weightStep, now)
            val sided = sidedSets(converted, sideFactor(base.weightMode, machine.weightMode), now)
            shiftedSets(sided, platformShift(base, machine), now).forEach { sets.upsert(it) }
        }
        takenPhotos.forEach {
            photoRows.add(Photo(it.id, owner, machine.id, it.takenAt, now, false), it.jpeg)
        }
        removedPhotos
            .filter { it.userId == owner }
            .forEach { photoRows.upsert(it.copy(deleted = true, updatedAt = now)) }
        onSaved(machine.id)
    }
}
