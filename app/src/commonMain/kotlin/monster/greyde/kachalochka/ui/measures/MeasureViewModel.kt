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
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.inPeriod
import monster.greyde.kachalochka.core.domain.measures.isLocked
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.calendar.DayUi
import monster.greyde.kachalochka.ui.calendar.monthRank
import monster.greyde.kachalochka.ui.calendar.monthWeeks
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.monthTitle
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.format.weekdayDate
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

/** [points] are the period's values, oldest first; [history] is open while shown. */
data class MeasureUi(
    val name: String,
    val title: String,
    val unit: String,
    val period: MeasurePeriod,
    val points: List<Pair<CalendarDay, Double>>,
    val latest: String?,
    val change: String?,
    val entry: MeasureEntryUi,
    val history: MeasureHistoryUi?,
    val editing: EditMeasureUi?,
    val deleting: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
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

private const val STEP = 0.5

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
    private var sex: Sex? = null

    /** Newest day first, as the repository reads them. */
    private var values: List<Measurement> = emptyList()
    private var period = MeasurePeriod.Quarter
    private var editing: EditMeasureUi? = null
    private var deleting = false
    private var loading: Job? = null

    /** The day the entry shows; null follows today. */
    private var entryDay: CalendarDay? = null

    /** Null shows the entry day's stored value. */
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
                measure = measures.all(owner).firstOrNull { it.id == measureId }
                sex = profiles.forOwner(owner)?.sex
                values = readValues()
                if (measure == null) mutableGone.value = true
                publish()
            }
    }

    private suspend fun readValues(): List<Measurement> =
        measurements.all(currentUser.id()).filter { it.measureId == measureId }

    fun choose(chosen: MeasurePeriod) {
        period = chosen
        publish()
    }

    fun typeValue(text: String) {
        typed = text
        publish()
    }

    /** An empty entry starts from the latest value, the likeliest next one. */
    fun stepValue(direction: Int) {
        val text = mutableState.value?.entry?.text ?: return
        val start = parseDecimal(text) ?: values.firstOrNull()?.value ?: 0.0
        typed = formatNumber((start + direction * STEP).coerceAtLeast(0.0))
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
        values = readValues()
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

    private fun deletable(shown: Measure): Boolean = shown.kind?.let { !isLocked(it, sex) } ?: true

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
        val name = shown.shownName()
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
                unit = shown.shownUnit(),
                period = period,
                points = shownPeriod.map { it.day to it.value },
                latest = values.firstOrNull()?.let { measureValue(it.value, shown.shownUnit()) },
                change =
                    if (first != null && last != null && first != last) {
                        measureDelta(last.value, first.value)
                    } else {
                        null
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
            )
    }

    private fun entry(today: CalendarDay): MeasureEntryUi {
        val day = entryDay ?: today
        val stored = storedOn(day)
        val text = typed ?: stored?.let { formatNumber(it.value) }.orEmpty()
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
