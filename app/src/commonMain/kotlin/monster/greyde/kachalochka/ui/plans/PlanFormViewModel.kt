package monster.greyde.kachalochka.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.PLAN_NAME_LENGTH
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.visitMachines
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.OwnMachines
import monster.greyde.kachalochka.ui.machine.ShownMachines
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** A typed name is written once typing stops for this long. */
internal val NAME_PAUSE = 1.seconds

data class PlanFormUiState(
    val name: String = "",
    val rows: List<PlanMachineUi> = emptyList(),
    val pinned: Boolean = false,
    val ordering: Boolean = false,
    val canDelete: Boolean = false,
    val confirmingDelete: Boolean = false,
    val done: Boolean = false,
)

data class PlanMachineUi(
    val id: MachineId,
    val name: String,
    val photo: Photo?,
)

/**
 * Writes the plan on every change of its machines or pin, and its name after a pause in typing, on
 * [commitName] and on [leave]. A new plan starts with [machine], or with the machines of the
 * account's visit on [fromVisit], when given.
 */
class PlanFormViewModel(
    private val planId: PlanId?,
    private val fromVisit: CalendarDay?,
    private val machine: MachineId?,
    private val plans: PlanRepository,
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
    private val utcOffset: UtcOffset,
    private val accounts: Accounts,
    private val clock: Clock,
    private val catalogue: MachineCatalogue,
    private val failures: FailureLog,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlanFormUiState())
    val state: StateFlow<PlanFormUiState> = mutableState

    // One at a time, in call order, so an older state never lands after a newer one.
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val newId = PlanId.random()

    private var opened = false
    private var loaded = false
    private var owner: UserId? = null
    private var stored: Plan? = null
    private var own: OwnMachines? = null
    private var reading: Job? = null
    private var pause: Job? = null
    private var name = ""
    private var machineIds: List<MachineId> = emptyList()
    private var pinned = false
    private var ordering = false
    private var confirmingDelete = false
    private var done = false

    init {
        viewModelScope.launch {
            for (task in queue) {
                try {
                    task()
                } catch (stopped: CancellationException) {
                    throw stopped
                } catch (failed: Exception) {
                    failures.record(failed)
                }
            }
        }
        viewModelScope.launch {
            accounts.activeId.collect { active ->
                if (!opened) {
                    opened = true
                    owner = active
                    serially(::load)
                } else if (active != owner) {
                    pause?.cancel()
                    done = true
                    publish()
                }
            }
        }
    }

    private suspend fun load() {
        own = catalogue.own(owner)
        stored =
            planId?.let { plans.byId(it) }?.takeIf { it.userId == owner && !it.deleted }
        loaded = true
        val found = stored
        if (found != null) {
            name = found.name
            machineIds = found.machineIds
            pinned = found.pinned
        } else if (planId != null) {
            done = true
        } else {
            machineIds = startMachines()
            // After a switch during the reads, the plan would go to the previous account.
            if (!done) write()
        }
        publish()
    }

    private suspend fun startMachines(): List<MachineId> {
        machine?.let { return listOf(it) }
        val day = fromVisit ?: return emptyList()
        val shown = visits.shownOn(owner, day, sets, utcOffset::at) ?: return emptyList()
        return visitMachines(shown.sets, shown.visit.planned)
    }

    fun typeName(text: String) {
        name = text.take(PLAN_NAME_LENGTH)
        publish()
        pause?.cancel()
        pause =
            viewModelScope.launch {
                delay(NAME_PAUSE)
                commitName()
            }
    }

    fun commitName() = store(leaving = false)

    /** Writes what is left to write and then closes the form. */
    fun leave() = store(leaving = true)

    /** The machine may have been created or taken from a friend in the picker just now. */
    fun add(id: MachineId) {
        if (id !in machineIds) machineIds = machineIds + id
        if (own?.machines?.any { it.id == id } == true) {
            publish()
        } else {
            reading?.cancel()
            reading =
                viewModelScope.launch {
                    own = catalogue.own(owner)
                    publish()
                }
        }
        store(leaving = false)
    }

    fun remove(id: MachineId) {
        machineIds = machineIds - id
        publish()
        store(leaving = false)
    }

    fun togglePin() {
        pinned = !pinned
        publish()
        store(leaving = false)
    }

    fun toggleOrdering() {
        ordering = !ordering
        publish()
    }

    fun move(
        from: Int,
        to: Int,
    ) {
        val shown = rows().map { it.id }.toMutableList()
        if (from !in shown.indices || to !in shown.indices) return
        shown.add(to, shown.removeAt(from))
        machineIds = shown
        publish()
        store(leaving = false)
    }

    fun askToDelete() {
        confirmingDelete = true
        publish()
    }

    fun cancelDelete() {
        confirmingDelete = false
        publish()
    }

    fun confirmDelete() {
        confirmingDelete = false
        publish()
        serially {
            val plan = stored
            if (plan != null && !plan.deleted && !done) {
                plans.upsert(plan.copy(deleted = true, updatedAt = clock.now()))
                done = true
                publish()
            }
        }
    }

    private fun serially(task: suspend () -> Unit) {
        queue.trySend(task)
    }

    private fun store(leaving: Boolean) =
        serially {
            try {
                reading?.join()
                if (loaded && !done) write()
            } finally {
                if (leaving) done = true
                publish()
            }
        }

    private suspend fun write() {
        val plan = stored
        val trimmed = name.trim()
        val live = rows().map { it.id }
        val now = clock.now()
        if (trimmed.isEmpty() && live.isEmpty()) {
            if (plan != null && !plan.deleted) {
                val gone = plan.copy(deleted = true, updatedAt = now)
                plans.upsert(gone)
                stored = gone
            }
            return
        }
        if (plan != null &&
            !plan.deleted &&
            plan.name == trimmed &&
            plan.pinned == pinned &&
            plan.machineIds.alive() == live
        ) {
            return
        }
        val next =
            plan?.copy(
                name = trimmed,
                machineIds = live,
                updatedAt = now,
                deleted = false,
                pinned = pinned,
            ) ?: Plan(newId, owner, trimmed, live, now, now, false, pinned)
        plans.upsert(next)
        stored = next
    }

    private fun List<MachineId>.alive(): List<MachineId> {
        val ids = own?.machines.orEmpty().mapTo(HashSet()) { it.id }
        return filter { it in ids }
    }

    // A machine deleted or merged away drops out of the plan.
    private fun rows(): List<PlanMachineUi> {
        val read = own ?: return emptyList()
        val byId = read.machines.associateBy { it.id }
        val shown = ShownMachines(read, null)
        return machineIds.mapNotNull { id ->
            byId[id]?.let { PlanMachineUi(it.id, it.name, shown.cover(it.id)) }
        }
    }

    private fun publish() {
        val rows = rows()
        mutableState.value =
            PlanFormUiState(
                name = name,
                rows = rows,
                pinned = pinned,
                ordering = ordering && rows.size > 1,
                canDelete = stored?.deleted == false,
                confirmingDelete = confirmingDelete,
                done = done,
            )
    }
}
