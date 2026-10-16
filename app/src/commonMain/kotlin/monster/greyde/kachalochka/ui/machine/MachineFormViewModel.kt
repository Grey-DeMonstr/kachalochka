package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.Friend
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
import monster.greyde.kachalochka.core.domain.gym.hasTag
import monster.greyde.kachalochka.core.domain.gym.photoOrder
import monster.greyde.kachalochka.core.domain.gym.platformShift
import monster.greyde.kachalochka.core.domain.gym.roundWeight
import monster.greyde.kachalochka.core.domain.gym.shiftedSets
import monster.greyde.kachalochka.core.domain.gym.sideFactor
import monster.greyde.kachalochka.core.domain.gym.sidedSets
import monster.greyde.kachalochka.core.domain.gym.sortedTags
import monster.greyde.kachalochka.core.domain.gym.suggestedTags
import monster.greyde.kachalochka.core.domain.gym.tagKey
import monster.greyde.kachalochka.core.domain.gym.toggledTag
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
    /** Every tag of the account's other machines, offered as chips. */
    val knownTags: Set<String> = emptySet(),
    val newTag: String = "",
    /** Friends' tags, offered until one is chosen and so becomes the account's own. */
    val friendTags: List<FriendTagUi> = emptyList(),
    /** Every tag of the machines the edited one is linked with. */
    val linkedTags: List<String> = emptyList(),
    /** The machine's tags when the form opened, which lead the chips so a tap never moves one. */
    val openedTags: Set<String> = emptySet(),
) {
    /** The tags the account would hold after a save; any other tag is deleted. */
    val accountTags: Set<String>
        get() = tags + knownTags

    private val heldOpenedTags: List<String>
        get() = sortedTags(openedTags).filter { hasTag(accountTags, it) }

    val suggestedTags: List<String>
        get() = suggestedTags(linkedTags, tags + heldOpenedTags)

    /** The opened tags still held, then the account's other tags that are not suggested. */
    val shownTags: List<String>
        get() {
            val opened = heldOpenedTags
            val suggested = suggestedTags
            return opened +
                sortedTags(accountTags).filterNot { hasTag(opened, it) || hasTag(suggested, it) }
        }

    val offeredFriendTags: List<FriendTagUi>
        get() {
            val shown = shownTags + suggestedTags
            return friendTags.filterNot { hasTag(shown, it.tag) }
        }

    fun chosen(tag: String): Boolean = hasTag(tags, tag)

    val platformWeightValue: Double?
        get() =
            if (platformWeight.isBlank()) 0.0 else parseDecimal(platformWeight)?.takeIf { it >= 0 }

    val weightStepValue: Double?
        get() = parseDecimal(weightStep)?.let(::roundWeight)?.takeIf { it > 0 }

    /** The values a save writes, without the offers around them; "20.0" is the same as "20". */
    val edits: MachineFormState
        get() =
            MachineFormState(
                name = name.trim(),
                setupNote = setupNote.trim(),
                weightMode = weightMode,
                platformWeight = platformWeightValue?.let(::formatNumber) ?: platformWeight,
                platformIncluded = platformIncluded,
                unit = unit,
                unitLabel = if (unit == WeightUnit.Custom) unitLabel.trim() else "",
                weightStep = weightStepValue?.let(::formatNumber) ?: weightStep,
                tags = tags,
            )

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
    private val machineLinks: MachineLinkRepository,
    private val photoRows: PhotoRepository,
    private val catalogue: MachineCatalogue,
    private val reads: MachineReads,
    private val sets: WorkoutSetRepository,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            MachineFormState(name = args.name, tags = args.tags.map(::tagKey).toSet()),
        )
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

    /** What the form started with, pre-filled or loaded, to tell whether back drops edits. */
    private var startEdits = mutableState.value.edits
    private var startCover: PhotoId? = null
    private val mutableConfirmingLeave = MutableStateFlow(false)
    val confirmingLeave: StateFlow<Boolean> = mutableConfirmingLeave

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
                    startCover = null
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
                    else ->
                        MachineFormState(
                            name = args.name,
                            tags = args.tags.map(::tagKey).toSet(),
                        )
                }
            val owner = currentUser.id()
            val known = machines.all(owner).filter { it.id != current?.id }.flatMap { it.tags }
            mutableState.value = shown.copy(knownTags = known.toSet(), openedTags = shown.tags)
            startEdits = shown.edits
            savedPhotos = current?.let { photoRows.forMachine(it.id) }.orEmpty()
            coverChoice = current?.coverPhoto
            startCover = coverChoice
            showPhotos()
            refreshLinks()
        }
    }

    /**
     * Takes a friend's machine's name, tags and how it counts its weight into the form, as typed
     * edits: saving then asks about recorded sets as any other edit does.
     */
    fun copySettings(friendMachine: MachineId) {
        viewModelScope.launch {
            val owner = currentUser.id() ?: return@launch
            val group = reading { catalogue.group(owner) }.getOrNull() ?: return@launch
            if (accounts.activeId.value != owner) return@launch
            val source = group.friends.firstOrNull { it.machine.id == friendMachine }?.machine
            source ?: return@launch
            update { form ->
                form.copy(
                    name = source.name,
                    tags = form.tags + source.tags.map(::tagKey),
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

    private fun refreshLinks() {
        readingLinks?.cancel()
        readingLinks = viewModelScope.launch { readLinks() }
    }

    /**
     * Own links are on hand offline; friends' links, machines, photos and tags need the network.
     * The last group read shows them at once, and the group is read once for all of them; with a
     * read on the way and none cached, the form keeps the friends' machines it names.
     */
    private suspend fun readLinks() {
        val owner = accounts.activeId.value
        val shown = existing?.takeIf { it.userId == owner }
        val ownLinks = machineLinks.all(owner)
        if (shown == null) {
            mutableLinking.value = LinkingUi(error = mutableLinking.value.error)
            update { it.copy(linkedTags = emptyList()) }
        } else {
            val ownTouch = ownLinks.any { it.touches(shown.id) }
            mutableLinking.value =
                mutableLinking.value.let {
                    it.copy(canLink = true, canUnlink = it.canUnlink || ownTouch)
                }
        }
        // Before the load the form has no machine yet, so it waits for the load's own read.
        if (!loaded) return
        reads.machines(owner).collect { read ->
            if (!read.waiting) showGroup(owner, shown, ownLinks, read)
        }
    }

    private fun showGroup(
        owner: UserId?,
        shown: Machine?,
        ownLinks: List<MachineLink>,
        read: MachineRead,
    ) {
        if (accounts.activeId.value != owner || existing?.id != shown?.id) return
        val group = read.group?.machines
        group?.let { update { form -> form.copy(friendTags = friendTagsOf(it)) } }
        if (shown == null) return
        if (group != null) {
            friendPhotos = friendPhotosOf(shown.id, group)
            showPhotos()
        }
        val visible = read.shown
        update { form -> form.copy(linkedTags = visible.linkedRows(shown.id).flatMap { it.tags }) }
        mutableLinking.value =
            mutableLinking.value.copy(
                linkedWith = group?.let { visible.linkedWith(shown.id) }.orEmpty(),
                canUnlink = (group?.links ?: ownLinks).any { it.touches(shown.id) },
            )
    }

    /** Each friend's tag once, from the first friend by name who has it. */
    private fun friendTagsOf(group: GroupMachines): List<FriendTagUi> =
        group.friends
            .sortedBy { it.owner.displayName.lowercase() }
            .flatMap { mate -> mate.machine.tags.map { FriendTagUi(it, mate.owner) } }
            .distinctBy { tagKey(it.tag) }
            .sortedBy { tagKey(it.tag) }

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

    fun confirmUnlink() {
        val shown = existing ?: return
        writes.launch {
            val owner = currentUser.id()
            machines.byId(shown.id)?.takeIf { it.userId == owner } ?: return@launch
            if (!catalogue.unlink(owner, shown.id)) {
                mutableLinking.value =
                    mutableLinking.value.copy(
                        confirmingUnlink = false,
                        error = AppStrings.current.offline,
                    )
                return@launch
            }
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

    /** True when the form may close now; otherwise it asks about the unsaved edits. */
    fun requestLeave(): Boolean {
        if (!changed()) return true
        mutableConfirmingLeave.value = true
        return false
    }

    private fun changed(): Boolean =
        mutableState.value.edits != startEdits ||
            takenPhotos.isNotEmpty() ||
            removedPhotos.isNotEmpty() ||
            coverChoice != startCover

    fun stay() {
        mutableConfirmingLeave.value = false
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

    fun toggleTag(tag: String) = update { it.copy(tags = toggledTag(it.tags, tagKey(tag))) }

    fun typeNewTag(text: String) =
        update { it.copy(newTag = text.take(MachineFormState.TAG_LENGTH)) }

    /** A typed tag is stored as its key, so one typed in another case is the existing one. */
    fun addNewTag() =
        update { form ->
            val tag = tagKey(form.newTag)
            val tags = if (tag.isEmpty() || hasTag(form.tags, tag)) form.tags else form.tags + tag
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
