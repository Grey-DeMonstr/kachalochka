package monster.greyde.kachalochka.ui.measures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.bodyFatHistory
import monster.greyde.kachalochka.core.domain.measures.periodStart
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.components.HistoryRowUi
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.math.round
import kotlin.time.Clock

/** [points] are the period's percents, oldest first; [results] are all of them, newest first. */
data class BodyFatUi(
    val title: String,
    val period: MeasurePeriod,
    val points: List<Pair<CalendarDay, Double>>,
    val latest: String?,
    val lastMeasured: String?,
    val change: String?,
    val results: List<HistoryRowUi>,
)

class BodyFatViewModel(
    private val method: BodyFatMethod,
    private val measures: MeasureRepository,
    private val measurements: MeasurementRepository,
    private val profiles: ProfileRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow<BodyFatUi?>(null)
    val state: StateFlow<BodyFatUi?> = mutableState

    /** Rounded to the tenth the screen shows, oldest first. */
    private var percents: List<Pair<CalendarDay, Double>> = emptyList()
    private var period = MeasurePeriod.Quarter
    private var loading: Job? = null

    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                val profile = profiles.forOwner(owner)
                val values = measurements.all(owner).groupBy { it.measureId }
                // Of two live measures of one kind, the first with values counts, as on the list.
                val perKind =
                    measures
                        .all(owner)
                        .filter { it.kind != null && values[it.id] != null }
                        .groupBy { it.kind!! }
                        .mapValues { (_, same) ->
                            values.getValue(same.first().id).associate { it.day to it.value }
                        }
                percents =
                    bodyFatHistory(
                        method,
                        profile?.sex,
                        profile?.birthDate,
                        profile?.heightCm,
                        perKind,
                    ).map { (day, percent) -> day to round(percent * 10) / 10 }
                publish()
            }
    }

    fun choose(chosen: MeasurePeriod) {
        period = chosen
        publish()
    }

    private fun publish() {
        val now = clock.now()
        val today = CalendarDay.of(now, utcOffset.at(now))
        val start = periodStart(period, today)
        val shown = percents.filter { (day, _) -> start == null || day >= start }
        val first = shown.firstOrNull()
        val last = shown.lastOrNull()
        val latest = percents.lastOrNull()
        mutableState.value =
            BodyFatUi(
                title = "${AppStrings.current.bodyFat} · ${methodName(method)}",
                period = period,
                points = shown,
                latest = latest?.let { fatPercent(it.second) },
                lastMeasured = latest?.let { lastMeasured(it.first, today) },
                change =
                    if (first != null && last != null && first != last) {
                        measureDelta(last.second, first.second)
                    } else {
                        null
                    },
                results =
                    percents.asReversed().map { (day, percent) ->
                        HistoryRowUi(day, dayMonthLabel(day, today.year), fatPercent(percent))
                    },
            )
    }
}
