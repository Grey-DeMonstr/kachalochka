package monster.greyde.kachalochka.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.weekdayDate
import kotlin.time.Clock

data class HomeUiState(
    val today: TodayUi,
)

/** [started] once today has a set or a started plan. */
data class TodayUi(
    val day: CalendarDay,
    val date: String,
    val started: Boolean,
)

class HomeViewModel(
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
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
            val shown = visits.shownOn(currentUser.id(), today, sets, utcOffset::at)
            val started = shown != null && (shown.sets.isNotEmpty() || shown.visit.planned.any())
            mutableState.value =
                HomeUiState(TodayUi(today, weekdayDate(today, withYear = true), started))
        }
    }
}
