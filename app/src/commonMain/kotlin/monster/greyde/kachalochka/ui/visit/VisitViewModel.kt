package monster.greyde.kachalochka.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.clockLabel
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.groupSummary
import monster.greyde.kachalochka.ui.format.machineTitle
import monster.greyde.kachalochka.ui.format.platformSuffix
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.shortSet
import monster.greyde.kachalochka.ui.format.weightCaption
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
    val machineId: MachineId,
    val name: String,
    val platformSuffix: String?,
    val setNumberLabel: String,
    val caption: String?,
    val previous: String?,
    val weight: String,
    val weightCaption: String,
    val reps: String,
    val editing: Boolean,
)

class VisitViewModel(
    private val visitId: VisitId,
    private val visits: VisitRepository,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val currentUser: CurrentUser,
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
    private var editing: WorkoutSet? = null
    private var expanded: Set<MachineId> = emptySet()
    private var values = SetValues(0.0, DEFAULT_REPS)

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
        val machine = selectedMachine() ?: return
        values = values.copy(weight = stepWeight(values.weight, machine.weightStep, direction))
        publish()
    }

    fun changeReps(direction: Int) {
        values = values.copy(reps = stepReps(values.reps, direction))
        publish()
    }

    fun toggleGroup(id: MachineId) {
        expanded = if (id in expanded) expanded - id else expanded + id
        publish()
    }

    fun save() {
        val machine = selectedMachine() ?: return
        writes.launch {
            val now = clock.now()
            val edited = editing
            if (edited == null) {
                sets.upsert(
                    WorkoutSet(
                        WorkoutSetId.random(),
                        currentUser.id(),
                        visitId,
                        machine.id,
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
        val current = visit ?: return
        writes.launch {
            val now = clock.now()
            visits.upsert(current.copy(endedAt = now, updatedAt = now))
            onEnded()
        }
    }

    private fun selectedMachine(): Machine? = selected?.let(machinesById::get)

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
        visit = visits.byId(visitId)
        machinesById = machines.all(currentUser.id()).associateBy { it.id }
        visitSets = sets.forVisit(visitId)
        val machine = selectedMachine()
        previousSets =
            machine?.let { previousVisitSets(sets.forMachine(it.id), visitId) }.orEmpty()
        if (reseed && machine != null) {
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
        visit ?: return
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
        val machine = selectedMachine() ?: return null
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
            machineId = machine.id,
            name = machine.name,
            platformSuffix = platformSuffix(machine),
            setNumberLabel = "подход $number",
            caption = caption,
            previous = previous,
            weight = formatNumber(values.weight),
            weightCaption = weightCaption(machine),
            reps = values.reps.toString(),
            editing = edited != null,
        )
    }
}
