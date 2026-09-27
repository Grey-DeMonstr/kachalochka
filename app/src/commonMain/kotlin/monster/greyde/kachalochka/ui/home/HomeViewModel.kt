package monster.greyde.kachalochka.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.summarize
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.machineCount
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.unitLabel
import kotlin.time.Clock

data class HomeUiState(
    val today: TodayUi,
)

/** [counts] and [lastSet] stay null until the day's first set. */
data class TodayUi(
    val day: CalendarDay,
    val counts: String?,
    val lastSet: String?,
)

class HomeViewModel(
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow<HomeUiState?>(null)
    val state: StateFlow<HomeUiState?> = mutableState

    init {
        viewModelScope.launch { sync.completed.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            val now = clock.now()
            val today = CalendarDay.of(now, utcOffset.at(now))
            val daySets =
                visits
                    .shownOn(currentUser.id(), today, utcOffset::at)
                    ?.let { sets.forVisit(it.id) }
                    .orEmpty()
            mutableState.value = HomeUiState(todayUi(today, daySets))
        }
    }

    private suspend fun todayUi(
        day: CalendarDay,
        daySets: List<WorkoutSet>,
    ): TodayUi {
        if (daySets.isEmpty()) return TodayUi(day, null, null)
        val summary = summarize(daySets)
        val lastSet =
            summary.lastSet?.let { set ->
                machines
                    .byId(
                        set.machineId,
                    )?.let { "${it.name} ${setValue(set.weight, set.reps, unitLabel(it))}" }
            }
        return TodayUi(
            day = day,
            counts = "${machineCount(summary.machineCount)} · ${setCount(summary.setCount)}",
            lastSet = lastSet,
        )
    }
}
