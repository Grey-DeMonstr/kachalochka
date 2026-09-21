package monster.greyde.kachalochka.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.DEFAULT_REPS
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.SetValues
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.calendarDaysBetween
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.minuteOfDay
import monster.greyde.kachalochka.core.domain.gym.previousVisitSets
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
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.groupSummary
import monster.greyde.kachalochka.ui.format.machineTitle
import monster.greyde.kachalochka.ui.format.platformSuffix
import monster.greyde.kachalochka.ui.format.saveLabel
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.shortSet
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.ownVisit
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.time.Clock
import kotlin.time.Duration

data class VisitUiState(
    val setCountLabel: String,
    val groups: List<SetGroupUi>,
    val sheet: SheetUi?,
)

data class SetGroupUi(
    val machineId: MachineId,
    val title: String,
    val summary: String,
    val expanded: Boolean,
    val sets: List<SetRowUi>,
)

data class SetRowUi(
    val id: WorkoutSetId,
    val title: String,
    val value: String,
    val selected: Boolean,
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
)

class VisitViewModel(
    private val visitId: VisitId,
    private val visits: VisitRepository,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val restTimer: RestTimer,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
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

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect { reload(reseed = true) }
        }
    }

    val selectedMachineId: MachineId? get() = selected

    fun refresh() {
        viewModelScope.launch { reload(reseed = false) }
    }

    fun selectMachine(id: MachineId) {
        selected = id
        editing = null
        viewModelScope.launch { reload(reseed = true) }
    }

    fun changeWeight(direction: Int) {
        val machine = open ?: return
        values = values.copy(weight = stepWeight(values.weight, machine.weightStep, direction))
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
        writes.launch {
            val now = clock.now()
            val edited = editing
            if (edited == null) {
                val owner = currentUser.id()
                sets.upsert(
                    WorkoutSet(
                        WorkoutSetId.random(),
                        owner,
                        startedVisitOf(owner).id,
                        ownMachine(owner, machine).id,
                        values.weight,
                        values.reps,
                        now,
                        now,
                        false,
                    ),
                )
                restTimer.start()
            } else {
                sets.upsert(
                    edited.copy(weight = values.weight, reps = values.reps, updatedAt = now),
                )
                editing = null
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

    fun editSet(id: WorkoutSetId) {
        val set = visitSets.firstOrNull { it.id == id } ?: return
        editing = set
        selected = set.machineId
        values = SetValues(set.weight, set.reps)
        refresh()
    }

    fun leaveEdit(): Boolean {
        if (editing == null) return false
        editing = null
        viewModelScope.launch { reload(reseed = true) }
        return true
    }

    fun deleteEditedSet() {
        val edited = editing ?: return
        writes.launch {
            sets.upsert(edited.copy(deleted = true, updatedAt = clock.now()))
            editing = null
            reload(reseed = true)
        }
    }

    fun endVisit(onEnded: () -> Unit) {
        val current = visit
        if (current == null) {
            onEnded()
            return
        }
        writes.launch {
            val now = clock.now()
            visits.upsert(current.copy(endedAt = now, updatedAt = now))
            onEnded()
        }
    }

    private suspend fun startedVisitOf(owner: UserId?): Visit {
        visits.ownVisit(visitId, owner)?.let { return it }
        val now = clock.now()
        return Visit(VisitId.random(), owner, now, null, now, false).also { visits.upsert(it) }
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
        val shown = visits.ownVisit(visitId, owner)
        visit = shown
        machinesById = machines.all(owner).associateBy { it.id }
        visitSets = shown?.let { sets.forVisit(it.id) }.orEmpty()
        val machine = machineOf(owner)
        open = machine
        selected = machine?.id ?: selected
        previousSets =
            machine
                ?.takeIf { it.userId == owner }
                ?.let { previousVisitSets(sets.forMachine(it.id), shown?.id ?: visitId) }
                .orEmpty()
        if (reseed && editing == null && machine != null) {
            values =
                suggestNextSet(
                    machine,
                    previousSets,
                    visitSets.filter { it.machineId == machine.id },
                )
        }
        publish()
    }

    private fun publish() {
        val offset = utcOffset.at(clock.now())
        mutableState.value =
            VisitUiState(
                setCountLabel = setCount(visitSets.size),
                groups = groupByMachine(visitSets).map { groupUi(it.machineId, it.sets) },
                sheet = sheetUi(offset),
            )
    }

    private fun groupUi(
        machineId: MachineId,
        machineSets: List<WorkoutSet>,
    ): SetGroupUi {
        val machine = machinesById[machineId]
        val title = machine?.let(::machineTitle).orEmpty()
        val unit = machine?.unit ?: WeightUnit.Kg
        return SetGroupUi(
            machineId = machineId,
            title = title,
            summary = groupSummary(machineSets, unit),
            expanded = machineId in expanded || machineSets.any { it.id == editing?.id },
            sets =
                machineSets.mapIndexed { index, set ->
                    SetRowUi(
                        set.id,
                        "$title · подход ${index + 1}",
                        setValue(set.weight, set.reps, unit),
                        set.id == editing?.id,
                    )
                },
        )
    }

    private fun sheetUi(offset: Duration): SheetUi? {
        val machine = open ?: return null
        val people = accountsUi(accounts.accounts.value, accounts.activeId.value)
        val onMachine = visitSets.filter { it.machineId == machine.id }
        val edited = editing
        val number = setNumber(edited, onMachine)
        val caption =
            if (edited == null) {
                machine.setupNote.ifBlank { null }
            } else {
                "Правка · записано ${clockLabel(minuteOfDay(edited.recordedAt, offset))}, " +
                    "было ${setValue(edited.weight, edited.reps, machine.unit)}"
            }
        val previous =
            previousSets.takeIf { edited == null && it.isNotEmpty() }?.let { previous ->
                val days = calendarDaysBetween(previous.last().recordedAt, clock.now(), offset)
                val day = daysAgoLabel(days).replaceFirstChar { it.uppercase() }
                (listOf(day) + previous.map { shortSet(it.weight, it.reps) }).joinToString(" · ")
            }
        return SheetUi(
            name = machine.name,
            platformSuffix = platformSuffix(machine),
            setNumberLabel = "подход $number",
            caption = caption,
            previous = previous,
            weight = formatNumber(values.weight),
            weightCaption = weightCaption(machine),
            reps = values.reps.toString(),
            editing = edited != null,
            people = people,
            saveLabel =
                saveLabel(
                    people.takeIf { it.size > 1 }?.firstOrNull { it.active }?.displayName,
                    edited != null,
                ),
        )
    }
}
