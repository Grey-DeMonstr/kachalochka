package monster.greyde.kachalochka.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.traineeActive
import monster.greyde.kachalochka.ui.format.planTitle
import monster.greyde.kachalochka.ui.strings.AppStrings

data class PlansUiState(
    val rows: List<PlanRowUi> = emptyList(),
    val loaded: Boolean = false,
    val confirmingStart: PlanId? = null,
)

data class PlanRowUi(
    val id: PlanId,
    val title: String,
    val count: String,
    val pinned: Boolean,
)

class PlansViewModel(
    private val plans: PlanRepository,
    private val machines: MachineRepository,
    private val placement: Placement,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)
    private var shown: List<Plan> = emptyList()
    private var shownFor: UserId? = null
    private var loading: Job? = null

    /** The list follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                val names = machines.all(owner).associate { it.id to it.name }
                val found = plans.all(owner)
                shown = found
                shownFor = owner
                mutableState.value =
                    mutableState.value.copy(
                        rows =
                            found.map { plan ->
                                val live = plan.machineIds.mapNotNull { names[it] }
                                PlanRowUi(
                                    plan.id,
                                    planTitle(plan.name, live),
                                    AppStrings.current.machines(live.size),
                                    plan.pinned,
                                )
                            },
                        loaded = true,
                        confirmingStart =
                            mutableState.value.confirmingStart?.takeIf { id ->
                                found.any { it.id == id }
                            },
                    )
            }
    }

    /** Starts a pinned plan at once and keeps it; asks first about any other. */
    fun start(
        id: PlanId,
        onStarted: (CalendarDay) -> Unit,
    ) {
        // A started plan is a visit, which a trainer does not record for a trainee.
        if (accounts.traineeActive()) return
        val plan = shown.firstOrNull { it.id == id } ?: return
        if (plan.pinned) {
            begin(plan, onStarted)
        } else {
            mutableState.value = mutableState.value.copy(confirmingStart = id)
        }
    }

    fun cancelStart() {
        mutableState.value = mutableState.value.copy(confirmingStart = null)
    }

    fun confirmStart(onStarted: (CalendarDay) -> Unit) {
        val id = mutableState.value.confirmingStart ?: return
        cancelStart()
        if (accounts.traineeActive()) return
        shown.firstOrNull { it.id == id }?.let { begin(it, onStarted) }
    }

    private fun begin(
        plan: Plan,
        onStarted: (CalendarDay) -> Unit,
    ) {
        val shownTo = shownFor
        writes.launch { placement.startPlan(shownTo, plan)?.let(onStarted) }
    }
}
