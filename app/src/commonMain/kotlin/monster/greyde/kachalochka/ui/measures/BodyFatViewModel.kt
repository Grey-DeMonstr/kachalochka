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
import monster.greyde.kachalochka.core.domain.measures.BodyInput
import monster.greyde.kachalochka.core.domain.measures.BodyInputs
import monster.greyde.kachalochka.core.domain.measures.Cycle
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.bodyFatHistory
import monster.greyde.kachalochka.core.domain.measures.cycleOf
import monster.greyde.kachalochka.core.domain.measures.missingInputs
import monster.greyde.kachalochka.core.domain.measures.periodStart
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.profile.ageOn
import monster.greyde.kachalochka.ui.components.HistoryRowUi
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.math.round
import kotlin.time.Clock

/**
 * [points] are the period's percents, oldest first; [results] are all of them, newest first.
 * [missing] names what the formula lacks; [profileIncomplete] when the profile holds some of it.
 */
data class BodyFatUi(
    val title: String,
    val period: MeasurePeriod,
    val points: List<Pair<CalendarDay, Double>>,
    val latest: String?,
    val lastMeasured: String?,
    val change: String?,
    val results: List<HistoryRowUi>,
    val missing: String?,
    val profileIncomplete: Boolean,
    val phases: CyclePhasesUi? = null,
)

private val profileInputs = setOf(BodyInput.Sex, BodyInput.Age, BodyInput.Height)

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
    private var missing: List<BodyInput> = emptyList()
    private var cycle: Cycle? = null
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
                val all = measurements.all(owner)
                val values = all.groupBy { it.measureId }
                val owned = measures.all(owner)
                cycle = cycleOf(profile, owned, all)
                // Of two live measures of one kind, the first with values counts, as on the list.
                val perKind =
                    owned
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

                fun latest(kind: MeasureKind): Double? =
                    perKind[kind]?.maxByOrNull { (day, _) -> day }?.value
                val inputs =
                    BodyInputs(
                        sex = profile?.sex,
                        age = profile?.birthDate?.let { ageOn(it, today()) },
                        heightCm = profile?.heightCm,
                        weightKg = latest(MeasureKind.Weight),
                        waistCm = latest(MeasureKind.Waist),
                        neckCm = latest(MeasureKind.Neck),
                        hipsCm = latest(MeasureKind.Hips),
                    )
                missing = missingInputs(method, inputs)
                publish()
            }
    }

    fun choose(chosen: MeasurePeriod) {
        period = chosen
        publish()
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }

    private fun publish() {
        val today = today()
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
                missing =
                    missing.takeIf { it.isNotEmpty() }?.let {
                        AppStrings.current.needs(it.map(::inputName))
                    },
                profileIncomplete = missing.any { it in profileInputs },
                phases = chartPhases(cycle, period, shown.map { it.first }),
            )
    }
}
