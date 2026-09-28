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
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.calendar.monthRank
import monster.greyde.kachalochka.ui.calendar.monthWeeks
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.monthTitle
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.format.weekdayName
import kotlin.time.Clock
import kotlin.time.Instant

/** [hint] is the measure's latest value before the form's day. */
data class MeasureFieldUi(
    val id: MeasureId,
    val name: String,
    val unit: String,
    val text: String,
    val hint: String?,
    val valid: Boolean,
    val kind: MeasureKind?,
)

data class MeasurementFormUi(
    val day: CalendarDay,
    val dayTitle: String,
    val fields: List<MeasureFieldUi>,
    val canSave: Boolean,
    val canDelete: Boolean,
    val picker: DayPickerUi?,
    val deleting: Boolean,
) {
    val picking: Boolean get() = picker != null
}

class MeasurementFormViewModel(
    initialDay: CalendarDay?,
    private val measures: MeasureRepository,
    private val measurements: MeasurementRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MeasurementFormUi?>(null)
    val state: StateFlow<MeasurementFormUi?> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var day: CalendarDay = initialDay ?: today()
    private var shown: List<Measure> = emptyList()

    /** The shown measures' values, newest day first. */
    private var values: List<Measurement> = emptyList()
    private var onDay: Map<MeasureId, Measurement> = emptyMap()
    private var texts: Map<MeasureId, String> = emptyMap()
    private var pickerMonth: CalendarMonth? = null
    private var deleting = false
    private var loading: Job? = null

    /** Another account has other measures, so a switch starts the form over. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    private fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                shown = measures.all(owner)
                val ids = shown.map { it.id }.toSet()
                values = measurements.all(owner).filter { it.measureId in ids }
                showDay()
            }
    }

    /** Anything typed for the previous day is dropped. */
    private fun showDay() {
        onDay = values.filter { it.day == day }.associateBy { it.measureId }
        texts = onDay.mapValues { formatNumber(it.value.value) }
        deleting = false
        publish()
    }

    fun type(
        id: MeasureId,
        text: String,
    ) {
        texts = texts + (id to text)
        publish()
    }

    fun pickDay() {
        pickerMonth = CalendarMonth.of(day)
        publish()
    }

    fun showMonth(direction: Int) {
        val next = pickerMonth?.plusMonths(direction) ?: return
        if (monthRank(next) > monthRank(CalendarMonth.of(today()))) return
        pickerMonth = next
        publish()
    }

    fun chooseDay(chosen: CalendarDay) {
        if (chosen > today()) return
        day = chosen
        pickerMonth = null
        showDay()
    }

    fun dismissPick() {
        pickerMonth = null
        publish()
    }

    fun save(onSaved: () -> Unit) {
        if (mutableState.value?.canSave != true) return
        writes.launch {
            val owner = currentUser.id()
            val now = clock.now()
            shown.forEach { measure ->
                val existing = onDay[measure.id]?.takeIf { it.userId == owner }
                val value = typedValue(measure.id)
                if (value == existing?.value) return@forEach
                val row =
                    when {
                        value == null -> existing?.copy(deleted = true, updatedAt = now)
                        existing != null -> existing.copy(value = value, updatedAt = now)
                        else -> newMeasurement(owner, measure.id, value, now)
                    }
                row?.let { measurements.upsert(it) }
            }
            requestSyncIfPast()
            onSaved()
        }
    }

    fun askDelete() {
        if (onDay.isEmpty()) return
        deleting = true
        publish()
    }

    fun cancelDelete() {
        deleting = false
        publish()
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        if (!deleting) return
        writes.launch {
            val now = clock.now()
            onDay.values.forEach { measurements.upsert(it.copy(deleted = true, updatedAt = now)) }
            requestSyncIfPast()
            deleting = false
            onDeleted()
        }
    }

    /** A past day's edit is a one-off, so it is pushed at once, as a past visit's is. */
    private fun requestSyncIfPast() {
        if (day != today()) sync.request()
    }

    private fun newMeasurement(
        owner: UserId?,
        measure: MeasureId,
        value: Double,
        now: Instant,
    ) = Measurement(MeasurementId.random(), owner, measure, day, value, now, false)

    private fun typedValue(id: MeasureId): Double? =
        texts[id].orEmpty().takeIf { it.isNotBlank() }?.let(::parseDecimal)

    private fun valid(id: MeasureId): Boolean {
        val text = texts[id].orEmpty()
        return text.isBlank() || parseDecimal(text) != null
    }

    private fun changed(id: MeasureId): Boolean = !valid(id) || typedValue(id) != onDay[id]?.value

    private fun publish() {
        val today = today()
        val fields =
            shown.map { measure ->
                MeasureFieldUi(
                    id = measure.id,
                    name = measure.name,
                    unit = measure.unit,
                    text = texts[measure.id].orEmpty(),
                    hint =
                        values
                            .firstOrNull { it.measureId == measure.id && it.day < day }
                            ?.let { formatNumber(it.value) },
                    valid = valid(measure.id),
                    kind = measure.kind,
                )
            }
        val label = dayMonthLabel(day, today.year)
        mutableState.value =
            MeasurementFormUi(
                day = day,
                dayTitle =
                    if (day ==
                        today
                    ) {
                        "Сегодня, $label"
                    } else {
                        "${weekdayName(day.dayOfWeek)}, $label"
                    },
                fields = fields,
                canSave = fields.all { it.valid } && shown.any { changed(it.id) },
                canDelete = onDay.isNotEmpty(),
                picker =
                    pickerMonth?.let { month ->
                        DayPickerUi(
                            monthTitle = monthTitle(month),
                            canShowNextMonth =
                                monthRank(month) < monthRank(CalendarMonth.of(today)),
                            weeks = monthWeeks(month, values.map { it.day }.toSet(), today, day),
                        )
                    },
                deleting = deleting,
            )
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }
}
