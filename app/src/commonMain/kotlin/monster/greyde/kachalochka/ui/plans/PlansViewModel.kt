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
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.planTitle
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.timer.RestTimer
import monster.greyde.kachalochka.ui.visit.SetRecorder
import kotlin.time.Clock

data class PlansUiState(
    val rows: List<PlanRowUi> = emptyList(),
    val loaded: Boolean = false,
    val confirmingStart: PlanId? = null,
)

data class PlanRowUi(
    val id: PlanId,
    val title: String,
    val count: String,
)

class PlansViewModel(
    private val plans: PlanRepository,
    private val visits: VisitRepository,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val restTimer: RestTimer,
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

    fun askToStart(id: PlanId) {
        mutableState.value = mutableState.value.copy(confirmingStart = id)
    }

    fun cancelStart() {
        mutableState.value = mutableState.value.copy(confirmingStart = null)
    }

    /**
     * Starts the plan [askToStart] asked about. The visit is written before the plan is deleted,
     * so a failed write keeps the plan.
     */
    fun start(onStarted: (CalendarDay) -> Unit) {
        val id = mutableState.value.confirmingStart ?: return
        cancelStart()
        val plan = shown.firstOrNull { it.id == id } ?: return
        val shownTo = shownFor
        writes.launch {
            val owner = currentUser.id()
            if (owner != shownTo) return@launch
            val now = clock.now()
            val today = CalendarDay.of(now, utcOffset.at(now))
            SetRecorder(today, visits, machines, sets, clock, utcOffset, restTimer, sync)
                .plan(owner, plan.machineIds)
            plans.upsert(plan.copy(deleted = true, updatedAt = clock.now()))
            onStarted(today)
        }
    }
}
