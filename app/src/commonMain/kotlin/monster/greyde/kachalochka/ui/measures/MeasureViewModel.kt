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
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.measures.Cycle
import monster.greyde.kachalochka.core.domain.measures.DEFAULT_CYCLE_LENGTH
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.cycleFrom
import monster.greyde.kachalochka.core.domain.measures.cycleOf
import monster.greyde.kachalochka.core.domain.measures.dayOn
import monster.greyde.kachalochka.core.domain.measures.inPeriod
import monster.greyde.kachalochka.core.domain.measures.isLocked
import monster.greyde.kachalochka.core.domain.measures.measureStep
import monster.greyde.kachalochka.core.domain.measures.periodStart
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.calendar.DayUi
import monster.greyde.kachalochka.ui.calendar.monthRank
import monster.greyde.kachalochka.ui.calendar.monthWeeks
import monster.greyde.kachalochka.ui.components.HistoryRowUi
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.monthTitle
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.format.weekdayDate
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.math.abs
import kotlin.time.Clock

/**
 * [points] are the period's values, oldest first; [results] are all values, newest first;
 * [history] is open while shown. [phasesOnly] draws the [phases] instead of the values.
 * [cycleDay] is today's day of the account's cycle, "День 12 из 28".
 */
data class MeasureUi(
    val name: String,
    val title: String,
    val unit: String,
    val hint: String?,
    val period: MeasurePeriod,
    val points: List<Pair<CalendarDay, Double>>,
    val latest: String?,
    val lastMeasured: String?,
    val change: String?,
    val results: List<HistoryRowUi>,
    val entry: MeasureEntryUi,
    val history: MeasureHistoryUi?,
    val editing: EditMeasureUi?,
    val deleting: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val phases: CyclePhasesUi? = null,
    val phasesOnly: Boolean = false,
    val cycleDay: String? = null,
)

/** The value typed for [day]; [canDelete] once the day has one stored. */
data class MeasureEntryUi(
    val day: CalendarDay,
    val label: String,
    val text: String,
    val canSave: Boolean,
    val canDelete: Boolean,
)

/** Days of the month with a value are marked as a visit is in the calendar. */
data class MeasureHistoryUi(
    val monthTitle: String,
    val canShowNextMonth: Boolean,
    val weeks: List<List<DayUi?>>,
)

data class EditMeasureUi(
    val name: String,
    val unit: String,
    val canSave: Boolean,
)

class MeasureViewModel(
    private val measureId: MeasureId,
    private val measures: MeasureRepository,
    private val measurements: MeasurementRepository,
    private val profiles: ProfileRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MeasureUi?>(null)
    val state: StateFlow<MeasureUi?> = mutableState

    /** Deleted here or elsewhere, or not the active account's: nothing is left to show. */
    private val mutableGone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = mutableGone
    private val writes = WriteGuard(viewModelScope)

    private var measure: Measure? = null
    private var profile: Profile? = null
    private var accountMeasures: List<Measure> = emptyList()

    /** The account's, from all its recorded days of the cycle. */
    private var cycle: Cycle? = null

    /** Newest day first, as the repository reads them. */
    private var values: List<Measurement> = emptyList()
    private var period = MeasurePeriod.Quarter
    private var editing: EditMeasureUi? = null
    private var deleting = false
    private var loading: Job? = null

    /** The day the entry shows; null follows today. */
    private var entryDay: CalendarDay? = null

    /** Null shows the entry day's start value. */
    private var typed: String? = null

    /** The history's month; null while the history is closed. */
    private var month: CalendarMonth? = null

    /** Another account cannot see this measure, so a switch leaves the screen. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                accountMeasures = measures.all(owner)
                measure = accountMeasures.firstOrNull { it.id == measureId }
                profile = profiles.forOwner(owner)
                readValues()
                if (measure == null) mutableGone.value = true
                publish()
            }
    }

    private suspend fun readValues() {
        val all = measurements.all(currentUser.id())
        values = all.filter { it.measureId == measureId }
        cycle = cycleOf(profile, accountMeasures, all)
    }

    fun choose(chosen: MeasurePeriod) {
        period = chosen
        publish()
    }

    fun typeValue(text: String) {
        typed = text
        publish()
    }

    /**
     * An empty field steps from the value nearest its day, the likeliest one. A day of the cycle
     * wraps within the cycle's length.
     */
    fun stepValue(direction: Int) {
        val entry = mutableState.value?.entry ?: return
        val nearest = values.minByOrNull { abs(it.day.epochDay - entry.day.epochDay) }
        val start = parseDecimal(entry.text) ?: nearest?.value ?: 0.0
        typed =
            if (measure?.kind == MeasureKind.CycleDay) {
                val length = profile?.cycleLength ?: DEFAULT_CYCLE_LENGTH
                formatNumber(((start.toInt() + direction - 1).mod(length) + 1).toDouble())
            } else {
                val step = measureStep(values.map { it.value })
                formatNumber((start + direction * step).coerceAtLeast(0.0))
            }
        publish()
    }

    fun saveValue() {
        val entry = mutableState.value?.entry?.takeIf { it.canSave } ?: return
        val value = parseDecimal(entry.text) ?: return
        writes.launch {
            val now = clock.now()
            val row =
                storedOn(entry.day)?.copy(value = value, updatedAt = now)
                    ?: Measurement(
                        MeasurementId.random(),
                        currentUser.id(),
                        measureId,
                        entry.day,
                        value,
                        now,
                        false,
                    )
            measurements.upsert(row)
            written(entry.day)
        }
    }

    fun deleteValue() {
        val day = mutableState.value?.entry?.day ?: return
        val existing = storedOn(day) ?: return
        writes.launch {
            measurements.upsert(existing.copy(deleted = true, updatedAt = clock.now()))
            written(day)
        }
    }

    /** A past day's edit is a one-off, so it is pushed at once, as a past visit's is. */
    private suspend fun written(day: CalendarDay) {
        if (day != today()) sync.request()
        typed = null
        readValues()
        publish()
    }

    private fun storedOn(day: CalendarDay): Measurement? = values.firstOrNull { it.day == day }

    fun openHistory() {
        month = CalendarMonth.of(today())
        publish()
    }

    /** False when the history is already closed, so back leaves the screen. */
    fun closeHistory(): Boolean {
        if (month == null) return false
        month = null
        entryDay = null
        typed = null
        publish()
        return true
    }

    fun showHistoryMonth(direction: Int) {
        val next = month?.plusMonths(direction) ?: return
        if (monthRank(next) > monthRank(CalendarMonth.of(today()))) return
        month = next
        publish()
    }

    fun chooseDay(day: CalendarDay) {
        if (month == null || day > today()) return
        entryDay = day
        typed = null
        publish()
    }

    /** Opens the history on [day]'s month with [day] in the entry. */
    fun openDay(day: CalendarDay) {
        if (day > today()) return
        month = CalendarMonth.of(day)
        chooseDay(day)
    }

    fun openEdit() {
        val shown = measure?.takeIf { it.kind == null } ?: return
        editing = EditMeasureUi(shown.name, shown.unit, true)
        publish()
    }

    fun typeName(text: String) = edit { it.copy(name = text, canSave = text.isNotBlank()) }

    fun typeUnit(text: String) = edit { it.copy(unit = text) }

    private fun edit(change: (EditMeasureUi) -> EditMeasureUi) {
        editing = editing?.let(change) ?: return
        publish()
    }

    fun dismissEdit() {
        editing = null
        publish()
    }

    fun confirmEdit() {
        val edited = editing?.takeIf { it.canSave } ?: return
        val shown = measure ?: return
        writes.launch {
            val renamed =
                shown.copy(
                    name = edited.name.trim(),
                    unit = edited.unit.trim(),
                    updatedAt = clock.now(),
                )
            measures.upsert(renamed)
            sync.request()
            measure = renamed
            editing = null
            publish()
        }
    }

    fun askDelete() {
        if (measure?.let(::deletable) != true) return
        deleting = true
        publish()
    }

    fun cancelDelete() {
        deleting = false
        publish()
    }

    fun confirmDelete() {
        val shown = measure?.takeIf { deleting } ?: return
        writes.launch {
            val now = clock.now()
            measures.upsert(shown.copy(deleted = true, updatedAt = now))
            values.forEach { measurements.upsert(it.copy(deleted = true, updatedAt = now)) }
            sync.request()
            deleting = false
            mutableGone.value = true
        }
    }

    private fun deletable(shown: Measure): Boolean =
        shown.kind?.let { !isLocked(it, profile?.sex) } ?: true

    private fun publish() {
        val shown = measure
        if (shown == null) {
            mutableState.value = null
            return
        }
        val today = today()
        val shownPeriod = inPeriod(values, period, today)
        val first = shownPeriod.firstOrNull()
        val last = shownPeriod.lastOrNull()
        val phasesOnly = shown.kind == MeasureKind.CycleDay
        val name = shown.shownName()
        val unit = shown.shownUnit()
        val shownMonth = month
        mutableState.value =
            MeasureUi(
                name = name,
                title =
                    if (shownMonth == null) {
                        name
                    } else {
                        "$name · ${AppStrings.current.history.lowercase()}"
                    },
                unit = unit,
                hint = shown.kind?.let(::howToMeasure),
                period = period,
                points = shownPeriod.map { it.day to it.value },
                latest = values.firstOrNull()?.let { measureValue(it.value, unit) },
                lastMeasured = values.firstOrNull()?.let { lastMeasured(it.day, today) },
                change =
                    if (first != null && last != null && first != last && !phasesOnly) {
                        measureDelta(last.value, first.value)
                    } else {
                        null
                    },
                results =
                    values.map {
                        HistoryRowUi(
                            it.day,
                            dayMonthLabel(it.day, today.year),
                            measureValue(it.value, unit),
                        )
                    },
                entry = entry(today),
                history =
                    shownMonth?.let {
                        MeasureHistoryUi(
                            monthTitle(it),
                            monthRank(it) < monthRank(CalendarMonth.of(today)),
                            monthWeeks(
                                it,
                                values.map { v -> v.day }.toSet(),
                                today,
                                entryDay ?: today,
                            ),
                        )
                    },
                editing = editing,
                deleting = deleting,
                canEdit = shown.kind == null,
                canDelete = deletable(shown),
                phases =
                    if (phasesOnly) {
                        ownPhases(today)
                    } else {
                        chartPhases(cycle, period, shownPeriod.map { it.day })
                    },
                phasesOnly = phasesOnly,
                cycleDay =
                    cycle?.let {
                        AppStrings.current.cycleDayOf(
                            it.dayOn(today),
                            it.length,
                        )
                    },
            )
    }

    private fun ownCycle(): Cycle? = cycleFrom(values, profile?.cycleLength)

    /** Over the period up to today; "Всё" starts with the first value. */
    private fun ownPhases(today: CalendarDay): CyclePhasesUi? {
        val cycle = ownCycle() ?: return null
        val start = periodStart(period, today) ?: values.last().day
        return cyclePhases(cycle, start, today)
    }

    /**
     * A day without a value starts from the one before it, the likeliest new value; a day of the
     * cycle starts from the day it should be.
     */
    private fun entry(today: CalendarDay): MeasureEntryUi {
        val day = entryDay ?: today
        val stored = storedOn(day)
        val start =
            stored?.value
                ?: if (measure?.kind == MeasureKind.CycleDay) {
                    ownCycle()?.dayOn(day)?.toDouble()
                } else {
                    values.firstOrNull { it.day < day }?.value
                }
        val text = typed ?: start?.let(::formatNumber).orEmpty()
        val value = parseDecimal(text)
        return MeasureEntryUi(
            day = day,
            label =
                if (day == today) {
                    "${AppStrings.current.todayTitle}, ${dayMonthLabel(day, today.year)}"
                } else {
                    weekdayDate(day, withYear = day.year != today.year)
                },
            text = text,
            canSave = value != null && value != stored?.value,
            canDelete = stored != null,
        )
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }
}
