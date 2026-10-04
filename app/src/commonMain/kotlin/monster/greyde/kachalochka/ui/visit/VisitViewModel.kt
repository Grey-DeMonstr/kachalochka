package monster.greyde.kachalochka.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.FriendResult
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.DEFAULT_REPS
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.SetValues
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.calendarDaysBetween
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.machineMovedTo
import monster.greyde.kachalochka.core.domain.gym.machinePeaks
import monster.greyde.kachalochka.core.domain.gym.plannedWithoutSets
import monster.greyde.kachalochka.core.domain.gym.previousVisitSets
import monster.greyde.kachalochka.core.domain.gym.roundWeight
import monster.greyde.kachalochka.core.domain.gym.setMovedTo
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.stepReps
import monster.greyde.kachalochka.core.domain.gym.stepWeight
import monster.greyde.kachalochka.core.domain.gym.suggestNextSet
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.account.AccountUi
import monster.greyde.kachalochka.ui.account.Nickname
import monster.greyde.kachalochka.ui.account.accountsUi
import monster.greyde.kachalochka.ui.format.SharedMachine
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.headedTagSections
import monster.greyde.kachalochka.ui.format.machineTitle
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.format.platformSuffix
import monster.greyde.kachalochka.ui.format.recordingConversion
import monster.greyde.kachalochka.ui.format.saveLabel
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.setsSummary
import monster.greyde.kachalochka.ui.format.setsSummaryParts
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.format.visitShareText
import monster.greyde.kachalochka.ui.format.weekdayDate
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.machine.GroupMachines
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.OwnMachines
import monster.greyde.kachalochka.ui.machine.ShownMachines
import monster.greyde.kachalochka.ui.share.TextSharing
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

const val COMMENT_LENGTH = 200

/** More reps than anyone does in one set: a typo, not a record. */
private const val MAX_REPS = 999

data class VisitUiState(
    /** The day, heading the list. */
    val title: String,
    val groups: List<SetGroupUi>,
    /** The machine being looked at, over the list; null shows the list. */
    val page: MachinePageUi?,
    val ordering: Boolean,
    val canShare: Boolean,
    val notice: String?,
    /** [groups] as shown: split by tag set when grouped, else one untitled section. */
    val sections: List<VisitSectionUi> = listOf(VisitSectionUi(null, groups)),
    val groupByTag: Boolean = false,
    val canGroupByTag: Boolean = false,
    val canOrder: Boolean = false,
)

data class VisitSectionUi(
    val title: String?,
    val groups: List<SetGroupUi>,
)

/** A machine's row; [summary] is its results, the weights and the reps, which may wrap apart. */
data class SetGroupUi(
    val machineId: MachineId,
    val title: String,
    val setupNote: String,
    val summary: List<String>,
    val sets: List<SetRowUi>,
    val photo: Photo? = null,
    val tags: List<String> = emptyList(),
    val planned: Boolean = false,
)

data class SetRowUi(
    val id: WorkoutSetId,
    val title: String,
    val value: String,
    val selected: Boolean,
    val comment: String = "",
)

data class FriendLineUi(
    val id: UserId,
    val name: String,
    val avatar: Avatar,
    val text: String,
)

data class MachinePageUi(
    val machineId: MachineId,
    val name: String,
    val platformSuffix: String?,
    val photo: Photo?,
    val tags: List<String>,
    val setupNote: String,
    /** The best set ever on the machine. */
    val record: String?,
    /** How long ago the previous visit on the machine was, over [previous], its results. */
    val previousAgo: String?,
    val previous: String?,
    val friends: List<FriendLineUi>,
    /** "Сегодня · 3 подхода" over [sets], the day's sets on the machine; null without any. */
    val daySets: String?,
    val sets: List<SetRowUi>,
    val canUnplan: Boolean,
    val form: SetFormUi?,
)

/** The set being added or corrected; [weight] and [reps] are as typed. */
data class SetFormUi(
    val number: String,
    val title: String,
    val weight: String,
    /** "−" before a gravitron's weight. */
    val weightSign: String,
    val weightUnit: String,
    /** The weight in the unit weights are shown in, when that is not the machine's own. */
    val converted: String?,
    val reps: String,
    val comment: String,
    val editing: Boolean,
    val people: List<AccountUi>,
    val saveLabel: String,
    val canSave: Boolean,
    val saving: Boolean,
)

class VisitViewModel(
    private val day: CalendarDay,
    private val visits: VisitRepository,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val restTimer: RestTimer,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
    private val friends: FriendsRepository,
    private val sharing: TextSharing,
    private val nickname: Nickname,
    private val catalogue: MachineCatalogue,
    private val profiles: ProfileRepository,
    private val avatars: AccountAvatars,
) : ViewModel() {
    private val mutableState = MutableStateFlow<VisitUiState?>(null)
    val state: StateFlow<VisitUiState?> = mutableState
    private val writes = WriteGuard(viewModelScope)
    private val recorder =
        SetRecorder(day, visits, machines, sets, clock, utcOffset, restTimer, sync)

    private var machinesById: Map<MachineId, Machine> = emptyMap()
    private var visitSets: List<WorkoutSet> = emptyList()
    private var shownVisit: Visit? = null
    private var previousSets: List<WorkoutSet> = emptyList()
    private var machineSets: List<WorkoutSet> = emptyList()
    private var selected: MachineId? = null
    private var open: Machine? = null
    private var formOpen = false
    private var editing: WorkoutSet? = null
    private var values = SetValues(0.0, DEFAULT_REPS)
    private var ordering = false
    private var saving = false
    private var commentText = ""
    private var friendResults: List<FriendResult> = emptyList()
    private var friendsFor: Pair<UserId, MachineId>? = null
    private var loadingFriends: Job? = null
    private var own: OwnMachines? = null

    /** Group mates' machines, links and photos, read for [groupFor]; null until the read lands. */
    private var group: GroupMachines? = null
    private var groupFor: UserId? = null
    private var loadingGroup: Job? = null

    /** Links may have changed on another screen or in a sync, so the next reload reads again. */
    private var friendsStale = false
    private var notice: String? = null
    private var preferred = PreferredWeightUnit.Kg
    private var groupByTag = false

    /** Counts switches, so a profile read before the latest one cannot undo it. */
    private var groupByTagSwitches = 0
    private var groupByTagWrite: Job? = null

    /**
     * Read ahead of the tap: a browser lets the clipboard be written only while a tap is recent,
     * which a network read of the profile could outlast.
     */
    private var sharer: Pair<UserId?, String>? = null

    /** The text as typed; null while the weight shows the stepped, formatted value. */
    private var weightText: String? = null
    private val typedWeight: Double? get() = weightText?.let(::parseDecimal)?.takeIf { it >= 0 }
    private val weightValid: Boolean get() = weightText == null || typedWeight != null

    /** The reps as typed; null while they show the stepped value. */
    private var repsText: String? = null
    private val typedReps: Int?
        get() = repsText?.trim()?.toIntOrNull()?.takeIf { it in 1..MAX_REPS }
    private val repsValid: Boolean get() = repsText == null || typedReps != null

    private val isToday: Boolean get() = day == today()

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                notice = null
                loadSharer(it)
                reload(reseed = true)
            }
        }
        // The weight and reps the user is choosing stay as they are.
        viewModelScope.launch {
            sync.completed.collect {
                friendsStale = true
                reload(reseed = false)
            }
        }
    }

    /** The machine last opened, which the picker offers to copy even once its page is closed. */
    private var lastOpened: MachineId? = null

    val selectedMachineId: MachineId? get() = selected ?: lastOpened

    fun refresh() {
        friendsStale = true
        reloadShown()
    }

    private fun reloadShown() {
        loadSharer(accounts.activeId.value)
        viewModelScope.launch { reload(reseed = false) }
    }

    fun share() {
        if (visitSets.isEmpty()) return
        val owner = accounts.activeId.value
        val name = sharer?.takeIf { it.first == owner }?.second.orEmpty()
        val shared =
            groupByMachine(visitSets).mapNotNull { group ->
                machinesById[group.machineId]?.let { SharedMachine(it, group.sets) }
            }
        val text = visitShareText(name, day, shared, preferred, groupByTag)
        writes.launch {
            notice = reading { sharing.share(text) }.getOrNull()
            publish()
        }
    }

    fun dismissNotice() {
        notice = null
        publish()
    }

    private fun loadSharer(owner: UserId?) {
        viewModelScope.launch {
            reading { nickname.of(owner) }.onSuccess { sharer = owner to it }
        }
    }

    /** Opens [id]'s page with the form for its next set, as a picked machine opens. */
    fun selectMachine(id: MachineId) = showMachine(id, withForm = true)

    /** Opens [id]'s page, as a tap on its row does. */
    fun openMachine(id: MachineId) = showMachine(id, withForm = false)

    private fun showMachine(
        id: MachineId,
        withForm: Boolean,
    ) {
        selected = id
        editing = null
        formOpen = withForm
        commentText = ""
        ordering = false
        viewModelScope.launch { reload(reseed = true) }
    }

    /** The form for the open machine's next set, from the last set's values. */
    fun openForm() {
        if (open == null) return
        formOpen = true
        editing = null
        commentText = ""
        viewModelScope.launch { reload(reseed = true) }
    }

    fun changeWeight(direction: Int) {
        val machine = open ?: return
        weightText = null
        values = values.copy(weight = stepWeight(values.weight, machine.weightStep, direction))
        publish()
    }

    fun typeWeight(text: String) {
        weightText = text
        typedWeight?.let { values = values.copy(weight = roundWeight(it)) }
        publish()
    }

    fun changeReps(direction: Int) {
        repsText = null
        values = values.copy(reps = stepReps(values.reps, direction))
        publish()
    }

    fun typeReps(text: String) {
        repsText = text
        typedReps?.let { values = values.copy(reps = it) }
        publish()
    }

    fun typeComment(text: String) {
        commentText = text.take(COMMENT_LENGTH)
        publish()
    }

    fun switchTo(id: UserId) {
        viewModelScope.launch { accounts.switchTo(id) }
    }

    fun save() {
        val machine = open ?: return
        if (!weightValid || !repsValid || !formOpen) return
        // Taken now: while the write waits on the network the page may move to another machine.
        val saved = values
        val comment = commentText.trim()
        val edited = editing
        writes.launch {
            saving = true
            publish()
            try {
                if (edited == null) {
                    recorder.record(currentUser.id(), machine, saved, comment)
                } else {
                    recorder.amend(edited, saved, comment)
                }
                val stillOpen = open?.id == machine.id
                if (stillOpen && editing?.id == edited?.id) {
                    commentText = ""
                    // A correction is done with; a new set is followed by the next one.
                    if (edited != null) closeForm()
                }
                reload(reseed = stillOpen)
            } finally {
                saving = false
                publish()
            }
        }
    }

    /** Configuring a machine is deliberate, so it may make the active account's copy of it. */
    fun openMachineSettings(
        id: MachineId,
        onOpen: (MachineId) -> Unit,
    ) {
        val machine = machinesById[id] ?: open?.takeIf { it.id == id } ?: return
        writes.launch {
            // Merged away on another screen since the list was read.
            if (machines.byId(machine.id)?.deleted == true) {
                reload(reseed = false)
                return@launch
            }
            val own = recorder.ownCopy(currentUser.id(), machine)
            reload(reseed = false)
            onOpen(own.id)
        }
    }

    /** Kept in the account's profile, so the choice follows it to its other devices. */
    fun toggleGroupByTag() {
        groupByTag = !groupByTag
        groupByTagSwitches++
        publish()
        val wanted = groupByTag
        val previous = groupByTagWrite
        groupByTagWrite =
            viewModelScope.launch {
                // One write at a time, so the last switch is the one the server keeps.
                previous?.join()
                writeGroupByTag(wanted)
            }
    }

    private suspend fun writeGroupByTag(wanted: Boolean) {
        val owner = currentUser.id()
        val now = clock.now()
        val stored = profiles.forOwner(owner) ?: Profile.new(owner, now)
        profiles.upsert(stored.copy(groupByTag = wanted, updatedAt = now))
        sync.request()
    }

    fun toggleOrdering() {
        ordering = !ordering
        if (ordering) closePage()
        publish()
    }

    fun moveMachine(
        id: MachineId,
        index: Int,
    ) = reorder { machineMovedTo(visitSets, id, index, it) }

    fun moveSet(
        id: WorkoutSetId,
        index: Int,
    ) = reorder { setMovedTo(visitSets, id, index, it) }

    private fun reorder(moved: (Instant) -> List<WorkoutSet>) {
        writes.launch {
            val changed = moved(clock.now())
            // Shown before the write, so the dropped item stays where the finger left it.
            val byId = changed.associateBy { it.id }
            visitSets = visitSets.map { byId[it.id] ?: it }
            publish()
            recorder.reorder(changed)
            reload(reseed = false)
        }
    }

    /** Opens [id] in the form, on its machine's page. */
    fun editSet(id: WorkoutSetId) {
        if (ordering) return
        val set = visitSets.firstOrNull { it.id == id } ?: return
        editing = set
        selected = set.machineId
        formOpen = true
        values = SetValues(set.weight, set.reps)
        weightText = null
        repsText = null
        commentText = set.comment
        reloadShown()
    }

    /**
     * Steps back once: an open form closes, else the open page does. Returns false when there was
     * neither, so back can leave the screen.
     */
    fun closeSheet(): Boolean {
        if (formOpen && open != null) {
            closeForm()
            publish()
            return true
        }
        if (open == null) return false
        closePage()
        publish()
        return true
    }

    /** Leaves the form, dropping any edit in it. */
    fun cancelForm() {
        closeForm()
        publish()
    }

    private fun closeForm() {
        formOpen = false
        editing = null
        weightText = null
        repsText = null
        commentText = ""
    }

    private fun closePage() {
        closeForm()
        selected = null
        open = null
    }

    fun deleteEditedSet() {
        val edited = editing ?: return
        writes.launch {
            recorder.remove(edited)
            closeForm()
            reload(reseed = true)
        }
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }

    /**
     * After a switch the page still shows the machine the previous account was on; the active
     * account's own row of that name stands in for it, until a save mirrors it (spec §4.1).
     */
    private suspend fun machineOf(owner: UserId?): Machine? {
        val requested = selected ?: return null
        machinesById[requested]?.let { return it }
        val shown = open ?: return null
        // Merged away or deleted on another screen: a set must not land on it.
        if (shown.userId == owner && machines.byId(shown.id)?.deleted == true) {
            closePage()
            return null
        }
        return machines.named(owner, shown.name) ?: shown
    }

    private fun setNumber(
        edited: WorkoutSet?,
        onMachine: List<WorkoutSet>,
    ): Int =
        if (edited == null) {
            onMachine.size + 1
        } else {
            onMachine.indexOfFirst { it.id == edited.id } + 1
        }

    private suspend fun reload(reseed: Boolean) {
        val owner = currentUser.id()
        val shown = visits.shownOn(owner, day, sets, utcOffset::at)
        val switches = groupByTagSwitches
        val profile = profiles.forOwner(owner)
        preferred = profile?.weightUnit ?: PreferredWeightUnit.Kg
        if (switches == groupByTagSwitches) groupByTag = profile?.groupByTag ?: false
        val ownRead = catalogue.own(owner)
        own = ownRead
        machinesById = ownRead.machines.associateBy { it.id }
        visitSets = shown?.sets.orEmpty()
        shownVisit = shown?.visit
        val machine = machineOf(owner)
        open = machine
        selected = machine?.id ?: selected
        machine?.let { lastOpened = it.id }
        machineSets =
            machine
                ?.takeIf { it.userId == owner }
                ?.let { sets.forMachine(it.id) }
                .orEmpty()
        previousSets =
            previousVisitSets(
                machineSets,
                shown?.visit?.id,
                before = day.at(0L, utcOffset.at(clock.now())),
            )
        if (reseed && editing == null && machine != null) {
            values =
                suggestNextSet(
                    machine,
                    previousSets,
                    visitSets.filter { it.machineId == machine.id },
                )
            weightText = null
            repsText = null
        }
        // Only an own machine has links worth asking about; a switch drops the old answer.
        val asked =
            owner?.let { me -> machine?.takeIf { it.userId == me }?.let { me to it.id } }
        val readGroup = owner != groupFor || friendsStale
        if (asked != friendsFor) {
            friendResults = emptyList()
            loadingFriends?.cancel()
            friendsFor = asked
            if (!readGroup) loadFriends()
        }
        if (readGroup) loadGroup(owner)
        friendsStale = false
        publish()
    }

    /** The friends' results on the open machine's cluster, asked once the group read is in. */
    private fun loadFriends() {
        val asked = friendsFor ?: return
        val (owner, machine) = asked
        val clusters = group?.takeIf { it.owner == owner }?.clusters ?: return
        val theirs = clusters.of(machine) - machinesById.keys
        loadingFriends?.cancel()
        loadingFriends =
            viewModelScope.launch {
                val results =
                    reading {
                        if (theirs.isEmpty()) emptyList() else friends.latestOn(owner, theirs)
                    }
                if (friendsFor != asked) return@launch
                // A failed re-read keeps what an earlier read of the same machine showed.
                results.onSuccess { found ->
                    friendResults = found
                    publish()
                }
            }
    }

    private fun loadGroup(owner: UserId?) {
        loadingGroup?.cancel()
        if (owner != groupFor) group = null
        groupFor = owner
        if (owner == null) return
        loadingGroup =
            viewModelScope.launch {
                reading { catalogue.group(owner) }.onSuccess { found ->
                    if (groupFor != owner) return@onSuccess
                    group = found
                    publish()
                    loadFriends()
                }
            }
    }

    private fun coverOf(machineId: MachineId): Photo? =
        own?.let { ShownMachines(it, group).cover(machineId) }

    private fun friendLine(result: FriendResult): FriendLineUi {
        val now = clock.now()
        val days = calendarDaysBetween(result.sets.last().recordedAt, now, utcOffset.at(now))
        val sets = setsSummary(result.machine, result.sets, preferred)
        return FriendLineUi(
            result.friend.userId,
            result.friend.displayName,
            result.friend.avatar,
            "${daysAgoLabel(days)} · $sets",
        )
    }

    private fun publish() {
        val now = clock.now()
        val offset = utcOffset.at(now)
        val recorded = groupByMachine(visitSets).map { groupUi(it.machineId, it.sets) }
        // A planned row among the dragged ones could be dropped where no machine can go.
        val planned =
            if (ordering) {
                emptyList()
            } else {
                plannedWithoutSets(shownVisit?.planned.orEmpty(), visitSets, machinesById.keys)
                    .map { plannedUi(it) }
            }
        val groups = recorded + planned
        mutableState.value =
            VisitUiState(
                title = weekdayDate(day, withYear = day.year != CalendarDay.of(now, offset).year),
                groups = groups,
                sections = sections(groups),
                groupByTag = groupByTag,
                canGroupByTag = groups.any { it.tags.isNotEmpty() },
                page = pageUi(offset),
                ordering = ordering,
                canShare = visitSets.isNotEmpty(),
                canOrder = visitSets.isNotEmpty(),
                notice = notice,
            )
    }

    private fun setRows(
        machine: Machine?,
        machineSets: List<WorkoutSet>,
    ): List<SetRowUi> =
        machineSets.mapIndexed { setIndex, set ->
            SetRowUi(
                set.id,
                "#${setIndex + 1}",
                machine?.let { setValue(set.weight, set.reps, it, preferred) }
                    ?: setValue(set.weight, set.reps, AppStrings.current.kg),
                set.id == editing?.id,
                set.comment,
            )
        }

    private fun groupUi(
        machineId: MachineId,
        machineSets: List<WorkoutSet>,
    ): SetGroupUi {
        val machine = machinesById[machineId]
        return SetGroupUi(
            machineId = machineId,
            title = machine?.let { machineTitle(it, preferred) }.orEmpty(),
            setupNote = machine?.setupNote.orEmpty(),
            summary = machine?.let { setsSummaryParts(it, machineSets, preferred) }.orEmpty(),
            sets = setRows(machine, machineSets),
            photo = coverOf(machineId),
            tags = machine?.tags.orEmpty().sortedBy { it.lowercase() },
        )
    }

    private fun plannedUi(machineId: MachineId): SetGroupUi {
        val machine = machinesById.getValue(machineId)
        return SetGroupUi(
            machineId = machineId,
            title = machineTitle(machine, preferred),
            setupNote = machine.setupNote,
            summary = listOf(AppStrings.current.planned),
            sets = emptyList(),
            photo = coverOf(machineId),
            tags = machine.tags.sortedBy { it.lowercase() },
            planned = true,
        )
    }

    /** Takes [id] out of the visit's plan, and its page with it. */
    fun unplan(id: MachineId) {
        val visit = shownVisit ?: return
        if (open?.id == id) closePage()
        writes.launch {
            recorder.unplan(visit.id, id)
            reload(reseed = false)
        }
    }

    private fun sections(groups: List<SetGroupUi>): List<VisitSectionUi> =
        if (groupByTag && !ordering) {
            headedTagSections(groups) { it.tags.toSet() }.map { (title, items) ->
                VisitSectionUi(title, items)
            }
        } else {
            listOf(VisitSectionUi(null, groups))
        }

    private fun pageUi(offset: Duration): MachinePageUi? {
        val machine = open ?: return null
        val onMachine = visitSets.filter { it.machineId == machine.id }
        val until = if (isToday) clock.now() else day.at(0L, offset)
        val previousAgo =
            previousSets.lastOrNull()?.let {
                daysAgoLabel(calendarDaysBetween(it.recordedAt, until, offset))
                    .replaceFirstChar { first -> first.uppercase() }
            }
        val dayName =
            if (isToday) {
                AppStrings.current.todayTitle
            } else {
                dayMonthLabel(day, CalendarDay.of(clock.now(), offset).year)
            }
        val planned = shownVisit?.planned.orEmpty()
        return MachinePageUi(
            machineId = machine.id,
            name = machine.name,
            platformSuffix = platformSuffix(machine, preferred),
            photo = coverOf(machine.id),
            tags = machine.tags.sortedBy { it.lowercase() },
            setupNote = machine.setupNote,
            record =
                machinePeaks(machineSets).firstOrNull()?.best(machine.weightMode)?.let {
                    setValue(it.weight, it.reps, machine, preferred)
                },
            previousAgo = previousAgo,
            previous =
                previousSets.takeIf { it.isNotEmpty() }?.let {
                    setsSummary(machine, it, preferred)
                },
            friends = if (editing == null) friendResults.map(::friendLine) else emptyList(),
            daySets =
                onMachine.takeIf { it.isNotEmpty() }?.let {
                    "$dayName · ${setCount(it.size)}"
                },
            sets = setRows(machine, onMachine),
            canUnplan = onMachine.isEmpty() && machine.id in planned,
            form = if (formOpen) formUi(machine, onMachine) else null,
        )
    }

    private fun formUi(
        machine: Machine,
        onMachine: List<WorkoutSet>,
    ): SetFormUi {
        val edited = editing
        val people =
            if (isToday && edited == null) {
                accountsUi(accounts.accounts.value, accounts.activeId.value, avatars.photos.value)
            } else {
                emptyList()
            }
        return SetFormUi(
            number = "#${setNumber(edited, onMachine)}",
            title =
                if (edited == null) {
                    AppStrings.current.newSet
                } else {
                    AppStrings.current.editOf(
                        setValue(edited.weight, edited.reps, machine, preferred),
                    )
                },
            weight = weightText ?: formatNumber(values.weight),
            weightSign = if (machine.weightMode == WeightMode.Counterweight) "−" else "",
            weightUnit = unitLabel(machine),
            converted = recordingConversion(machine, values.weight, preferred),
            reps = repsText ?: values.reps.toString(),
            comment = commentText,
            editing = edited != null,
            people = people,
            saveLabel =
                saveLabel(
                    people.takeIf { it.size > 1 }?.firstOrNull { it.active }?.displayName,
                    edited != null,
                ),
            canSave = weightValid && repsValid && !saving,
            saving = saving,
        )
    }
}
