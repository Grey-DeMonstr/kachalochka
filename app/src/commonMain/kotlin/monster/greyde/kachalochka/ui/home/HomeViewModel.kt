package monster.greyde.kachalochka.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.summarize
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.machineCount
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import kotlin.time.Clock

data class HomeUiState(
    val activeVisit: ActiveVisitUi?,
)

data class ActiveVisitUi(
    val id: VisitId,
    val counts: String,
    val lastSet: String?,
)

class HomeViewModel(
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val clock: Clock,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow<HomeUiState?>(null)
    val state: StateFlow<HomeUiState?> = mutableState
    private val writes = WriteGuard(viewModelScope)

    init {
        viewModelScope.launch { sync.completed.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            val active = visits.active(currentUser.id())
            mutableState.value = HomeUiState(active?.let { activeVisitUi(it) })
        }
    }

    fun startVisit(onStarted: (VisitId) -> Unit) {
        writes.launch {
            val now = clock.now()
            val visit = Visit(VisitId.random(), currentUser.id(), now, null, now, false)
            visits.upsert(visit)
            onStarted(visit.id)
        }
    }

    private suspend fun activeVisitUi(visit: Visit): ActiveVisitUi {
        val summary = summarize(sets.forVisit(visit.id))
        val lastSet =
            summary.lastSet?.let { set ->
                machines
                    .byId(
                        set.machineId,
                    )?.let { "${it.name} ${setValue(set.weight, set.reps, it.unit)}" }
            }
        return ActiveVisitUi(
            id = visit.id,
            counts = "${machineCount(summary.machineCount)} · ${setCount(summary.setCount)}",
            lastSet = lastSet,
        )
    }
}
