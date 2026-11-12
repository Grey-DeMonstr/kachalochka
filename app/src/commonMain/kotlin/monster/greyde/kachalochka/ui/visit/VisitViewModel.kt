package monster.greyde.kachalochka.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.gym.GymWrites
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.FriendRecord
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
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
import monster.greyde.kachalochka.core.domain.gym.bestSet
import monster.greyde.kachalochka.core.domain.gym.calendarDaysBetween
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.lastPyramidStart
import monster.greyde.kachalochka.core.domain.gym.machineMovedTo
import monster.greyde.kachalochka.core.domain.gym.machinePeaks
import monster.greyde.kachalochka.core.domain.gym.plannedWithoutSets
import monster.greyde.kachalochka.core.domain.gym.platformShift
import monster.greyde.kachalochka.core.domain.gym.previousVisitSets
import monster.greyde.kachalochka.core.domain.gym.pyramidGroups
import monster.greyde.kachalochka.core.domain.gym.roundWeight
import monster.greyde.kachalochka.core.domain.gym.setMovedTo
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.sortedTags
import monster.greyde.kachalochka.core.domain.gym.suggestNextSet
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.account.AccountUi
import monster.greyde.kachalochka.ui.account.Nickname
import monster.greyde.kachalochka.ui.account.UnsavedChoices
import monster.greyde.kachalochka.ui.account.accountsUi
import monster.greyde.kachalochka.ui.account.groupByTagChoice
import monster.greyde.kachalochka.ui.account.traineeActive
import monster.greyde.kachalochka.ui.calendar.VisitCardUi
import monster.greyde.kachalochka.ui.calendar.ownCard
import monster.greyde.kachalochka.ui.calendar.ownPerson
import monster.greyde.kachalochka.ui.format.DeviceClock
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.headedTagSections
import monster.greyde.kachalochka.ui.format.platformSuffix
import monster.greyde.kachalochka.ui.format.recordingConversion
import monster.greyde.kachalochka.ui.format.saveLabel
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.setsSummary
import monster.greyde.kachalochka.ui.format.setsSummaryParts
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.format.weekdayDate
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.MachineReads
import monster.greyde.kachalochka.ui.machine.OwnMachines
import monster.greyde.kachalochka.ui.machine.ShownMachines
import monster.greyde.kachalochka.ui.plans.Placement
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.time.Duration
import kotlin.time.Instant

data class VisitUiState(
    /** The day, heading the list. */
    val title: String,
    val groups: List<SetGroupUi>,
    /** The machine being looked at, over the list; null shows the list. */
    val page: MachinePageUi?,
    val ordering: Boolean,
    /** The visit as its calendar card, which the share popup offers; null without sets. */
    val card: VisitCardUi?,
    /** [groups] as shown: split by tag set when grouped, else one untitled section. */
    val sections: List<VisitSectionUi> = listOf(VisitSectionUi(null, groups)),
    val groupByTag: Boolean = false,
    val canGroupByTag: Boolean = false,
    val canOrder: Boolean = false,
    val canRepeatToday: Boolean = false,
    val isToday: Boolean = false,
    /** A trainee's visit, which their trainer only looks at. */
    val readOnly: Boolean = false,
)

data class VisitSectionUi(
    val title: String?,
    val groups: List<SetGroupUi>,
)

/** A machine's row; [summary] is its results, the weights and the reps, which may wrap apart. */
data class SetGroupUi(
    val machineId: MachineId,
    val name: String,
    val setupNote: String,
    val summary: List<String>,
    val sets: List<SetRowUi>,
    val photo: Photo? = null,
    val tags: List<String> = emptyList(),
    val planned: Boolean = false,
    val platformSuffix: String? = null,
) {
    val title: String get() = platformSuffix?.let { "$name $it" } ?: name
}

data class SetRowUi(
    val id: WorkoutSetId,
    val title: String,
    val value: String,
    val selected: Boolean,
    val comment: String = "",
    /** The set starts a pyramid. */
    val pyramid: Boolean = false,
)

/** A pyramid's row: "#4–6", its best set, and whether its sets show under it. */
data class PyramidUi(
    val start: WorkoutSetId,
    val numbers: String,
    val best: String,
    val expanded: Boolean,
)

/** One set alone, or a pyramid with its sets. */
data class SetBlockUi(
    val pyramid: PyramidUi?,
    val sets: List<SetRowUi>,
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
    /** "Today · 3 sets" over [sets], the day's sets on the machine; null without any. */
    val daySets: String?,
    val sets: List<SetRowUi>,
    val canUnplan: Boolean,
    val form: SetFormUi?,
    val readOnly: Boolean = false,
    /** The day's sets on the page show drag handles. */
    val ordering: Boolean = false,
    val canOrder: Boolean = false,
    /** The visit's machines in the list's order, which a swipe goes through; empty outside it. */
    val swipeOrder: List<SetGroupUi> = emptyList(),
    /** [sets] cut into pyramids, as the page shows them outside ordering. */
    val blocks: List<SetBlockUi> = emptyList(),
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
    val pyramid: Boolean,
    val pyramidLabel: String,
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
    private val clock: DeviceClock,
    private val sync: SyncTrigger,
    private val friends: FriendsRepository,
    private val nickname: Nickname,
    private val catalogue: MachineCatalogue,
    private val reads: MachineReads,
    private val profiles: ProfileRepository,
    private val avatars: AccountAvatars,
    private val placement: Placement,
    gymWrites: GymWrites,
    unsaved: UnsavedChoices,
) : ViewModel() {
    private val mutableState = MutableStateFlow<VisitUiState?>(null)
    val state: StateFlow<VisitUiState?> = mutableState
    private val writes = WriteGuard(viewModelScope)
    private val recorder =
        SetRecorder(day, visits, machines, sets, clock, restTimer, sync, gymWrites)

    private var machinesById: Map<MachineId, Machine> = emptyMap()
    private var visitSets: List<WorkoutSet> = emptyList()
    private var shownVisit: Visit? = null
    private var previousSets: List<WorkoutSet> = emptyList()
    private var machineSets: List<WorkoutSet> = emptyList()
    private var selected: MachineId? = null
    private var open: Machine? = null
    private val form = SetForm()
    private var ordering = false
    private var orderingSets = false
    private var saving = false

    /** Pyramids opened or closed by a tap, against their default. */
    private var toggledPyramids = emptySet<WorkoutSetId>()
    private val groupReads = GroupReads(friends, reads, viewModelScope, ::publish)
    private var own: OwnMachines? = null
    private var preferred = PreferredWeightUnit.Kg
    private val grouping =
        groupByTagChoice(profiles, currentUser, clock, sync, unsaved, viewModelScope)
    private val groupByTag: Boolean get() = grouping.current
    private var sharer = ""

    private val isToday: Boolean get() = day == clock.today()

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                reload(reseed = true)
            }
        }
        // The weight and reps the user is choosing stay as they are.
        viewModelScope.launch {
            sync.completed.collect {
                groupReads.markStale()
                reload(reseed = false)
            }
        }
    }

    /** The machine last opened, which the picker offers to copy even once its page is closed. */
    private var lastOpened: MachineId? = null

    val selectedMachineId: MachineId? get() = selected ?: lastOpened

    fun refresh() {
        groupReads.markStale()
        reloadShown()
    }

    /** Plans this visit's machines in today's visit, then reports today. */
    fun repeatToday(onDone: (CalendarDay) -> Unit) {
        if (readOnly) return
        val owner = accounts.activeId.value
        writes.launch { placement.repeatToday(owner, day)?.let(onDone) }
    }

    private fun reloadShown() {
        viewModelScope.launch { reload(reseed = false) }
    }

    /**
     * Opens [id]'s page with the form for its next set, as a picked machine opens. The machine
     * stays in the visit as planned until it gets a set.
     */
    fun selectMachine(id: MachineId) = showMachine(id, withForm = !readOnly, plan = !readOnly)

    // A trainer looks at a trainee's visit and records nothing in it.
    private val readOnly: Boolean get() = accounts.traineeActive()

    /** Opens [id]'s page, as a tap on its row does. */
    fun openMachine(id: MachineId) = showMachine(id, withForm = false)

    private fun showMachine(
        id: MachineId,
        withForm: Boolean,
        plan: Boolean = false,
    ) {
        selected = id
        toggledPyramids = emptySet()
        if (withForm) form.openNew() else form.close()
        ordering = false
        orderingSets = false
        viewModelScope.launch {
            if (plan) planOwnCopy(id)
            reload(reseed = true)
        }
    }

    private suspend fun planOwnCopy(id: MachineId) {
        val owner = currentUser.id()
        val machine = machines.byId(id) ?: return
        recorder.plan(owner, listOf(catalogue.ownCopy(owner, machine).id))
    }

    /** The form for the open machine's next set, from the last set's values. */
    fun openForm() {
        if (open == null || readOnly || orderingSets) return
        form.openNew()
        viewModelScope.launch { reload(reseed = true) }
    }

    fun changeWeight(direction: Int) {
        val machine = open ?: return
        form.changeWeight(machine.weightStep, direction)
        publish()
    }

    fun typeWeight(text: String) {
        form.typeWeight(text)
        publish()
    }

    fun changeReps(direction: Int) {
        form.changeReps(direction)
        publish()
    }

    fun typeReps(text: String) {
        form.typeReps(text)
        publish()
    }

    fun typeComment(text: String) {
        form.typeComment(text)
        publish()
    }

    /** A new pyramid of a new set starts from the first step of the last one. */
    fun togglePyramidStart() {
        val machine = open ?: return
        form.togglePyramid()
        if (form.pyramid && form.editing == null) {
            lastPyramidStart(visitSets.filter { it.machineId == machine.id })?.let {
                form.suggest(SetValues(it.weight, it.reps))
            }
        }
        publish()
    }

    fun togglePyramidOpen(start: WorkoutSetId) {
        toggledPyramids =
            if (start in toggledPyramids) toggledPyramids - start else toggledPyramids + start
        publish()
    }

    fun switchTo(id: UserId) {
        viewModelScope.launch { accounts.switchTo(id) }
    }

    fun save() {
        val machine = open ?: return
        if (!form.valid || !form.isOpen || readOnly) return
        // Taken now: while the write waits on the network the page may move to another machine.
        val saved = form.values
        val comment = form.comment.trim()
        val pyramid = form.pyramid
        val edited = form.editing
        writes.launch {
            saving = true
            publish()
            try {
                if (edited == null) {
                    val owner = currentUser.id()
                    val own = catalogue.ownCopy(owner, machine)
                    recorder.record(owner, own, saved, comment, pyramid)
                } else {
                    recorder.amend(edited, saved, comment, pyramid)
                }
                val stillOpen = open?.id == machine.id
                if (stillOpen && form.editing?.id == edited?.id) {
                    form.clearForNext()
                    // A correction is done with; a new set is followed by the next one.
                    if (edited != null) form.close()
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
        if (readOnly) return
        val machine = machinesById[id] ?: open?.takeIf { it.id == id } ?: return
        writes.launch {
            // Merged away on another screen since the list was read.
            if (machines.byId(machine.id)?.deleted == true) {
                reload(reseed = false)
                return@launch
            }
            val own = catalogue.ownCopy(currentUser.id(), machine)
            reload(reseed = false)
            onOpen(own.id)
        }
    }

    /** Kept in the account's profile, so the choice follows it to its other devices. */
    fun toggleGroupByTag() {
        grouping.choose(!groupByTag)
        publish()
    }

    fun toggleOrdering() {
        if (readOnly && !ordering) return
        ordering = !ordering
        if (ordering) closePage()
        publish()
    }

    fun moveMachine(
        id: MachineId,
        index: Int,
    ) = reorder { machineMovedTo(visitSets, id, index, it) }

    /** Turns the drag handles of the open page's sets on or off; the form closes. */
    fun toggleSetOrdering() {
        if (readOnly && !orderingSets) return
        orderingSets = !orderingSets
        form.close()
        publish()
    }

    fun moveSet(
        id: WorkoutSetId,
        index: Int,
    ) = reorder { setMovedTo(visitSets, id, index, it) }

    private fun reorder(moved: (Instant) -> List<WorkoutSet>) {
        if (readOnly) return
        writes.launch {
            val changed = moved(clock.now())
            // Shown before the write, so the dropped item stays where the finger left it.
            val byId = changed.associateBy { it.id }
            visitSets = visitSets.map { byId[it.id] ?: it }.sortedWith(visitOrder)
            publish()
            recorder.reorder(changed)
            reload(reseed = false)
        }
    }

    /** Opens [id] in the form, on its machine's page. */
    fun editSet(id: WorkoutSetId) {
        if (ordering || orderingSets || readOnly) return
        val set = visitSets.firstOrNull { it.id == id } ?: return
        form.openOn(set)
        selected = set.machineId
        reloadShown()
    }

    /**
     * Steps back once: an order mode ends, else an open form closes, else the open page does.
     * Returns false when there was none of them, so back can leave the screen.
     */
    fun closeSheet(): Boolean {
        when {
            ordering -> ordering = false
            orderingSets -> orderingSets = false
            open == null -> return false
            form.isOpen -> form.close()
            else -> closePage()
        }
        publish()
        return true
    }

    /** Leaves the form, dropping any edit in it. */
    fun cancelForm() {
        form.close()
        publish()
    }

    private fun closePage() {
        form.close()
        orderingSets = false
        selected = null
        open = null
    }

    fun deleteEditedSet() {
        val edited = form.editing ?: return
        writes.launch {
            recorder.remove(edited)
            form.close()
            reload(reseed = true)
        }
    }

    /**
     * After a switch the page still shows the machine the previous account was on; the active
     * account's machine a save would use stands in for it, until a save copies it (spec §4.1).
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
        return catalogue.deviceStandIn(owner, shown) ?: shown
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
        val shown = visits.shownOn(owner, day, sets, clock::offsetAt)
        preferred = profiles.forOwner(owner)?.weightUnit ?: PreferredWeightUnit.Kg
        sharer = nickname.of(owner)
        grouping.read(owner)
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
                before = day.at(0L, clock.offsetAt(clock.now())),
            )
        if (reseed && form.editing == null && machine != null) {
            form.suggest(
                suggestNextSet(
                    machine,
                    previousSets,
                    visitSets.filter { it.machineId == machine.id },
                ),
            )
        }
        // Only an own machine has links worth asking about; a switch drops the old answer.
        val ownOpen = machine?.takeIf { owner != null && it.userId == owner }?.id
        groupReads.follow(owner, ownOpen)
        publish()
    }

    /** [record]'s weight counts on [mine]'s platform, so it compares with the viewer's own. */
    private fun friendLine(
        record: FriendRecord,
        mine: Machine,
    ): FriendLineUi {
        val shift = -platformShift(mine, record.machine)
        val weight = roundWeight(record.record.weight + shift).coerceAtLeast(0.0)
        return FriendLineUi(
            record.friend.userId,
            record.friend.displayName,
            record.friend.avatar,
            setValue(weight, record.record.reps, record.machine, preferred),
        )
    }

    private fun publish() {
        val now = clock.now()
        val offset = clock.offsetAt(now)
        val shown = own?.let { ShownMachines(it, groupReads.group) }
        val recorded = groupByMachine(visitSets).map { groupUi(it.machineId, it.sets, shown) }
        // A planned row among the dragged ones could be dropped where no machine can go.
        val planned =
            if (ordering) {
                emptyList()
            } else {
                plannedWithoutSets(shownVisit?.planned.orEmpty(), visitSets, machinesById.keys)
                    .map { plannedUi(it, shown) }
            }
        val groups = recorded + planned
        val sections = sections(groups)
        mutableState.value =
            VisitUiState(
                title = weekdayDate(day, withYear = day.year != CalendarDay.of(now, offset).year),
                groups = groups,
                sections = sections,
                groupByTag = groupByTag,
                canGroupByTag = groups.any { it.tags.isNotEmpty() },
                page = pageUi(offset, sections.flatMap { it.groups }, shown),
                ordering = ordering,
                card = card(now, offset),
                canOrder = visitSets.isNotEmpty() && !readOnly,
                canRepeatToday = groups.isNotEmpty() && !isToday && !readOnly,
                isToday = isToday,
                readOnly = readOnly,
            )
    }

    private fun card(
        now: Instant,
        offset: Duration,
    ): VisitCardUi? {
        val visit = shownVisit?.takeIf { visitSets.isNotEmpty() } ?: return null
        return ownCard(
            visit,
            visitSets,
            day,
            CalendarDay.of(now, offset),
            ownPerson(accounts, avatars, accounts.activeId.value),
            sharer,
            machinesById,
            preferred,
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
                set.id == form.editing?.id,
                set.comment,
                set.pyramid,
            )
        }

    /** The last pyramid shows open unless tapped closed, the others the other way round. */
    private fun setBlocks(
        machine: Machine,
        onMachine: List<WorkoutSet>,
        rows: List<SetRowUi>,
    ): List<SetBlockUi> {
        val groups = pyramidGroups(onMachine)
        val lastStart =
            groups
                .lastOrNull { it.pyramid }
                ?.sets
                ?.first()
                ?.id
        var end = 0
        return groups.map { group ->
            val from = end
            end += group.sets.size
            val start = group.sets.first().id
            val pyramid =
                group.takeIf { it.pyramid }?.let {
                    val best = checkNotNull(bestSet(it.sets, machine.weightMode))
                    PyramidUi(
                        start,
                        if (end - from == 1) "#$end" else "#${from + 1}–$end",
                        setValue(best.weight, best.reps, machine, preferred),
                        (start == lastStart) != (start in toggledPyramids),
                    )
                }
            SetBlockUi(pyramid, rows.subList(from, end))
        }
    }

    private fun groupUi(
        machineId: MachineId,
        machineSets: List<WorkoutSet>,
        shown: ShownMachines?,
    ): SetGroupUi {
        val machine = machinesById[machineId]
        return SetGroupUi(
            machineId = machineId,
            name = machine?.name.orEmpty(),
            platformSuffix = machine?.let { platformSuffix(it, preferred) },
            setupNote = machine?.setupNote.orEmpty(),
            summary = machine?.let { setsSummaryParts(it, machineSets, preferred) }.orEmpty(),
            sets = setRows(machine, machineSets),
            photo = shown?.cover(machineId),
            tags = sortedTags(machine?.tags.orEmpty()),
        )
    }

    private fun plannedUi(
        machineId: MachineId,
        shown: ShownMachines?,
    ): SetGroupUi {
        val machine = machinesById.getValue(machineId)
        return SetGroupUi(
            machineId = machineId,
            name = machine.name,
            platformSuffix = platformSuffix(machine, preferred),
            setupNote = machine.setupNote,
            summary = listOf(AppStrings.current.planned),
            sets = emptyList(),
            photo = shown?.cover(machineId),
            tags = sortedTags(machine.tags),
            planned = true,
        )
    }

    /** Takes [id] out of the visit's plan, and its page with it. */
    fun unplan(id: MachineId) {
        if (readOnly) return
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

    private fun pageUi(
        offset: Duration,
        listed: List<SetGroupUi>,
        shown: ShownMachines?,
    ): MachinePageUi? {
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
        val rows = setRows(machine, onMachine)
        return MachinePageUi(
            machineId = machine.id,
            name = machine.name,
            platformSuffix = platformSuffix(machine, preferred),
            photo = shown?.cover(machine.id),
            tags = sortedTags(machine.tags),
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
            friends =
                if (form.editing ==
                    null
                ) {
                    groupReads.records().map { friendLine(it, machine) }
                } else {
                    emptyList()
                },
            daySets =
                onMachine.takeIf { it.isNotEmpty() }?.let {
                    "$dayName · ${setCount(it.size)}"
                },
            sets = rows,
            canUnplan = onMachine.isEmpty() && machine.id in planned && !readOnly,
            form = if (form.isOpen && !readOnly) formUi(machine, onMachine) else null,
            readOnly = readOnly,
            ordering = orderingSets,
            canOrder = onMachine.size > 1 && !readOnly,
            swipeOrder = listed.takeIf { machine.id in it.map(SetGroupUi::machineId) }.orEmpty(),
            blocks = setBlocks(machine, onMachine, rows),
        )
    }

    private fun formUi(
        machine: Machine,
        onMachine: List<WorkoutSet>,
    ): SetFormUi {
        val edited = form.editing
        val people =
            if (isToday && edited == null) {
                // Nobody records for a trainee here.
                accountsUi(accounts.accounts.value, accounts.activeId.value, avatars.photos.value)
                    .filterNot { it.trainee }
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
            weight = form.weightShown,
            weightSign = if (machine.weightMode == WeightMode.Counterweight) "−" else "",
            weightUnit = unitLabel(machine),
            converted = recordingConversion(machine, form.values.weight, preferred),
            reps = form.repsShown,
            comment = form.comment,
            pyramid = form.pyramid,
            pyramidLabel =
                if (edited == null && onMachine.any { it.pyramid }) {
                    AppStrings.current.startNewPyramid
                } else {
                    AppStrings.current.startPyramid
                },
            editing = edited != null,
            people = people,
            saveLabel = saveLabel(edited != null),
            canSave = form.valid && !saving,
            saving = saving,
        )
    }
}
