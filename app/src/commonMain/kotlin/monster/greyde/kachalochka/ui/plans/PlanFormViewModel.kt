package monster.greyde.kachalochka.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.PLAN_NAME_LENGTH
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.OwnMachines
import monster.greyde.kachalochka.ui.machine.ShownMachines
import kotlin.time.Clock

data class PlanFormUiState(
    val name: String = "",
    val rows: List<PlanMachineUi> = emptyList(),
    val ordering: Boolean = false,
    val canSave: Boolean = false,
    val canDelete: Boolean = false,
    val confirmingDelete: Boolean = false,
    val done: Boolean = false,
)

data class PlanMachineUi(
    val id: MachineId,
    val name: String,
    val photo: Photo?,
)

/** Edits stay in the form until saved; a switch of account closes it unsaved. */
class PlanFormViewModel(
    private val planId: PlanId?,
    private val plans: PlanRepository,
    private val accounts: Accounts,
    private val clock: Clock,
    private val catalogue: MachineCatalogue,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlanFormUiState())
    val state: StateFlow<PlanFormUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var opened = false
    private var owner: UserId? = null
    private var stored: Plan? = null
    private var own: OwnMachines? = null
    private var reading: Job? = null
    private var name = ""
    private var machineIds: List<MachineId> = emptyList()
    private var ordering = false
    private var confirmingDelete = false
    private var done = false

    init {
        viewModelScope.launch {
            accounts.activeId.collect { active ->
                if (!opened) {
                    opened = true
                    owner = active
                    load()
                } else if (active != owner) {
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
        val found = stored
        if (found != null) {
            name = found.name
            machineIds = found.machineIds
        } else if (planId != null) {
            done = true
        }
        publish()
    }

    fun typeName(text: String) {
        name = text.take(PLAN_NAME_LENGTH)
        publish()
    }

    /** The machine may have been created or taken from a friend in the picker just now. */
    fun add(id: MachineId) {
        if (id !in machineIds) machineIds = machineIds + id
        if (own?.machines?.any { it.id == id } == true) {
            publish()
            return
        }
        reading?.cancel()
        reading =
            viewModelScope.launch {
                own = catalogue.own(owner)
                publish()
            }
    }

    fun remove(id: MachineId) {
        machineIds = machineIds - id
        publish()
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
    }

    fun save() {
        writes.launch {
            reading?.join()
            val live = rows().map { it.id }
            if (live.isEmpty()) return@launch
            val now = clock.now()
            val trimmed = name.trim()
            plans.upsert(
                stored?.copy(name = trimmed, machineIds = live, updatedAt = now)
                    ?: Plan(PlanId.random(), owner, trimmed, live, now, now, false),
            )
            done = true
            publish()
        }
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
        val plan = stored ?: return
        confirmingDelete = false
        writes.launch {
            plans.upsert(plan.copy(deleted = true, updatedAt = clock.now()))
            done = true
            publish()
        }
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
                ordering = ordering && rows.size > 1,
                canSave = rows.isNotEmpty(),
                canDelete = stored != null,
                confirmingDelete = confirmingDelete,
                done = done,
            )
    }
}
