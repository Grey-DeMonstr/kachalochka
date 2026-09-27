package monster.greyde.kachalochka.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.DEFAULT_REPS
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.SetValues
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.calendarDaysBetween
import monster.greyde.kachalochka.core.domain.gym.dayVisit
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.machineMoved
import monster.greyde.kachalochka.core.domain.gym.minuteOfDay
import monster.greyde.kachalochka.core.domain.gym.nextPosition
import monster.greyde.kachalochka.core.domain.gym.previousVisitSets
import monster.greyde.kachalochka.core.domain.gym.recordingInstant
import monster.greyde.kachalochka.core.domain.gym.roundWeight
import monster.greyde.kachalochka.core.domain.gym.setMoved
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.stepReps
import monster.greyde.kachalochka.core.domain.gym.stepWeight
import monster.greyde.kachalochka.core.domain.gym.suggestNextSet
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.AccountUi
import monster.greyde.kachalochka.ui.account.accountsUi
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.clockLabel
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.groupSummary
import monster.greyde.kachalochka.ui.format.machineTitle
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.format.platformSuffix
import monster.greyde.kachalochka.ui.format.saveLabel
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.shortSet
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

data class VisitUiState(
    val title: String,
    val setCountLabel: String,
    val groups: List<SetGroupUi>,
    val sheet: SheetUi?,
    val ordering: Boolean,
)

data class SetGroupUi(
    val machineId: MachineId,
    val title: String,
    val summary: String,
    val expanded: Boolean,
    val sets: List<SetRowUi>,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

data class SetRowUi(
    val id: WorkoutSetId,
    val title: String,
    val value: String,
    val selected: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

data class SheetUi(
    val name: String,
    val platformSuffix: String?,
    val setNumberLabel: String,
    val caption: String?,
    val previous: String?,
    val weight: String,
    val weightCaption: String,
    val reps: String,
    val editing: Boolean,
    val people: List<AccountUi>,
    val saveLabel: String,
    val expanded: Boolean,
    val canSave: Boolean,
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
) : ViewModel() {
    private val mutableState = MutableStateFlow<VisitUiState?>(null)
    val state: StateFlow<VisitUiState?> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var visit: Visit? = null
    private var machinesById: Map<MachineId, Machine> = emptyMap()
    private var visitSets: List<WorkoutSet> = emptyList()
    private var previousSets: List<WorkoutSet> = emptyList()
    private var selected: MachineId? = null
    private var open: Machine? = null
    private var editing: WorkoutSet? = null
    private var expanded: Set<MachineId> = emptySet()
    private var values = SetValues(0.0, DEFAULT_REPS)
    private var sheetExpanded = true
    private var ordering = false

    /** The text as typed; null while the weight shows the stepped, formatted value. */
    private var weightText: String? = null
    private val typedWeight: Double? get() = weightText?.let(::parseDecimal)?.takeIf { it >= 0 }
    private val weightValid: Boolean get() = weightText == null || typedWeight != null

    private val isToday: Boolean get() = day == today()

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect { reload(reseed = true) }
        }
        // The weight and reps the user is choosing stay as they are.
        viewModelScope.launch {
            sync.completed.collect { reload(reseed = false) }
        }
    }

    val selectedMachineId: MachineId? get() = selected

    fun refresh() {
        viewModelScope.launch { reload(reseed = false) }
    }

    fun selectMachine(id: MachineId) {
        selected = id
        editing = null
        sheetExpanded = true
        ordering = false
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
        values = values.copy(reps = stepReps(values.reps, direction))
        publish()
    }

    fun switchTo(id: UserId) {
        viewModelScope.launch { accounts.switchTo(id) }
    }

    fun toggleGroup(id: MachineId) {
        expanded = if (id in expanded) expanded - id else expanded + id
        publish()
    }

    fun save() {
        val machine = open ?: return
        if (!weightValid) return
        writes.launch {
            val now = clock.now()
            val edited = editing
            if (edited == null) {
                val owner = currentUser.id()
                val offset = utcOffset.at(now)
                val today = CalendarDay.of(now, offset)
                val target =
                    visits.shownOn(owner, day, sets, utcOffset::at)
                        ?: dayVisit(day, owner, today, offset, now).also { visits.upsert(it) }
                val targetSets =
                    if (target.id == visit?.id) visitSets else sets.forVisit(target.id)
                sets.upsert(
                    WorkoutSet(
                        WorkoutSetId.random(),
                        owner,
                        target.id,
                        ownMachine(owner, machine).id,
                        values.weight,
                        values.reps,
                        nextPosition(targetSets),
                        recordingInstant(target, targetSets, today, now),
                        now,
                        false,
                    ),
                )
                if (day == today) restTimer.start() else sync.request()
            } else {
                sets.upsert(
                    edited.copy(weight = values.weight, reps = values.reps, updatedAt = now),
                )
                editing = null
                requestSyncIfPast()
            }
            reload(reseed = true)
        }
    }

    /** Configuring a machine is deliberate, so it may make the active account's copy of it. */
    fun openMachineSettings(onOpen: (MachineId) -> Unit) {
        val machine = open ?: return
        writes.launch {
            val own = ownMachine(currentUser.id(), machine)
            reload(reseed = false)
            onOpen(own.id)
        }
    }

    fun toggleOrdering() {
        ordering = !ordering
        if (ordering) collapseSheet()
        publish()
    }

    fun moveMachine(
        id: MachineId,
        direction: Int,
    ) = reorder { machineMoved(visitSets, id, direction, it) }

    fun moveSet(
        id: WorkoutSetId,
        direction: Int,
    ) = reorder { setMoved(visitSets, id, direction, it) }

    private fun reorder(moved: (Instant) -> List<WorkoutSet>) {
        writes.launch {
            val changed = moved(clock.now())
            changed.forEach { sets.upsert(it) }
            if (changed.isNotEmpty()) requestSyncIfPast()
            reload(reseed = false)
        }
    }

    fun editSet(id: WorkoutSetId) {
        if (ordering) return
        val set = visitSets.firstOrNull { it.id == id } ?: return
        editing = set
        selected = set.machineId
        values = SetValues(set.weight, set.reps)
        weightText = null
        sheetExpanded = true
        refresh()
    }

    fun collapseSheet(): Boolean {
        if (open == null || !sheetExpanded) return false
        sheetExpanded = false
        if (editing == null) {
            publish()
        } else {
            editing = null
            viewModelScope.launch { reload(reseed = true) }
        }
        return true
    }

    fun expandSheet() {
        if (open == null) return
        sheetExpanded = true
        ordering = false
        publish()
    }

    fun deleteEditedSet() {
        val edited = editing ?: return
        writes.launch {
            sets.upsert(edited.copy(deleted = true, updatedAt = clock.now()))
            editing = null
            requestSyncIfPast()
            reload(reseed = true)
        }
    }

    /** A past day's edit is a one-off, so it is pushed at once rather than with today's sets. */
    private fun requestSyncIfPast() {
        if (!isToday) sync.request()
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }

    /**
     * After a switch the sheet still shows the machine the previous account was on; the active
     * account's own row of that name stands in for it, until a save mirrors it (spec §4.1).
     */
    private suspend fun machineOf(owner: UserId?): Machine? {
        val requested = selected ?: return null
        machinesById[requested]?.let { return it }
        val shown = open ?: return null
        return machines.named(owner, shown.name) ?: shown
    }

    private suspend fun ownMachine(
        owner: UserId?,
        shown: Machine,
    ): Machine {
        if (shown.userId == owner) return shown
        machines.named(owner, shown.name)?.let { return it }
        val mirrored = shown.copy(id = MachineId.random(), userId = owner, updatedAt = clock.now())
        machines.upsert(mirrored)
        return mirrored
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
        visit = shown
        machinesById = machines.all(owner).associateBy { it.id }
        visitSets = shown?.let { sets.forVisit(it.id) }.orEmpty()
        val machine = machineOf(owner)
        open = machine
        selected = machine?.id ?: selected
        previousSets =
            machine
                ?.takeIf { it.userId == owner }
                ?.let {
                    previousVisitSets(
                        sets.forMachine(it.id),
                        shown?.id,
                        before = day.at(0L, utcOffset.at(clock.now())),
                    )
                }.orEmpty()
        if (reseed && editing == null && machine != null) {
            values =
                suggestNextSet(
                    machine,
                    previousSets,
                    visitSets.filter { it.machineId == machine.id },
                )
            weightText = null
        }
        publish()
    }

    private fun publish() {
        val now = clock.now()
        val offset = utcOffset.at(now)
        mutableState.value =
            VisitUiState(
                title =
                    if (isToday) {
                        "Сегодня"
                    } else {
                        "Визит · ${dayMonthLabel(day, CalendarDay.of(now, offset).year)}"
                    },
                setCountLabel = setCount(visitSets.size),
                groups =
                    groupByMachine(visitSets).let { groups ->
                        groups.mapIndexed { index, group ->
                            groupUi(group.machineId, group.sets, index, groups.lastIndex)
                        }
                    },
                sheet = sheetUi(offset),
                ordering = ordering,
            )
    }

    private fun groupUi(
        machineId: MachineId,
        machineSets: List<WorkoutSet>,
        index: Int,
        lastIndex: Int,
    ): SetGroupUi {
        val machine = machinesById[machineId]
        val title = machine?.let(::machineTitle).orEmpty()
        val unit = machine?.let(::unitLabel) ?: "кг"
        return SetGroupUi(
            machineId = machineId,
            title = title,
            summary = groupSummary(machineSets, unit),
            expanded =
                ordering || machineId in expanded || machineSets.any { it.id == editing?.id },
            sets =
                machineSets.mapIndexed { setIndex, set ->
                    SetRowUi(
                        set.id,
                        "$title · подход ${setIndex + 1}",
                        setValue(set.weight, set.reps, unit),
                        set.id == editing?.id,
                        canMoveUp = setIndex > 0,
                        canMoveDown = setIndex < machineSets.lastIndex,
                    )
                },
            canMoveUp = index > 0,
            canMoveDown = index < lastIndex,
        )
    }

    private fun sheetUi(offset: Duration): SheetUi? {
        val machine = open ?: return null
        val people =
            if (isToday) {
                accountsUi(accounts.accounts.value, accounts.activeId.value)
            } else {
                emptyList()
            }
        val onMachine = visitSets.filter { it.machineId == machine.id }
        val edited = editing
        val number = setNumber(edited, onMachine)
        val caption =
            if (edited == null) {
                machine.setupNote.ifBlank { null }
            } else {
                "Правка · записано ${clockLabel(minuteOfDay(edited.recordedAt, offset))}, " +
                    "было ${setValue(edited.weight, edited.reps, unitLabel(machine))}"
            }
        val previous =
            previousSets.takeIf { edited == null && it.isNotEmpty() }?.let { previous ->
                val until = if (isToday) clock.now() else day.at(0L, offset)
                val days = calendarDaysBetween(previous.last().recordedAt, until, offset)
                val ago = daysAgoLabel(days).replaceFirstChar { it.uppercase() }
                (listOf(ago) + previous.map { shortSet(it.weight, it.reps) }).joinToString(" · ")
            }
        return SheetUi(
            name = machine.name,
            platformSuffix = platformSuffix(machine),
            setNumberLabel = "подход $number",
            caption = caption,
            previous = previous,
            weight = weightText ?: formatNumber(values.weight),
            weightCaption = weightCaption(machine),
            reps = values.reps.toString(),
            editing = edited != null,
            people = people,
            saveLabel =
                saveLabel(
                    people.takeIf { it.size > 1 }?.firstOrNull { it.active }?.displayName,
                    edited != null,
                ),
            expanded = sheetExpanded,
            canSave = weightValid,
        )
    }
}
