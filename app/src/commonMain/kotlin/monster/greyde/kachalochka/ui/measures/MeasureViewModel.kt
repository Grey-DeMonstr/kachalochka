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
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.inPeriod
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import kotlin.time.Clock

/** [points] are the period's values, oldest first; [history] is every value, newest first. */
data class MeasureUi(
    val name: String,
    val unit: String,
    val period: MeasurePeriod,
    val points: List<Pair<CalendarDay, Double>>,
    val latest: String?,
    val change: String?,
    val history: List<HistoryRowUi>,
    val editing: EditMeasureUi?,
    val deleting: Boolean,
)

data class HistoryRowUi(
    val day: CalendarDay,
    val label: String,
    val value: String,
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
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MeasureUi?>(null)
    val state: StateFlow<MeasureUi?> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var measure: Measure? = null

    /** Newest day first, as the repository reads them. */
    private var values: List<Measurement> = emptyList()
    private var period = MeasurePeriod.Quarter
    private var editing: EditMeasureUi? = null
    private var deleting = false
    private var loading: Job? = null

    /** Another account cannot see this measure, so a switch empties the screen. */
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
                values = measurements.all(owner).filter { it.measureId == measureId }
                publish()
            }
    }

    fun choose(chosen: MeasurePeriod) {
        period = chosen
        publish()
    }

    fun openEdit() {
        val shown = measure ?: return
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
            measure = renamed
            editing = null
            publish()
        }
    }

    fun askDelete() {
        if (measure == null) return
        deleting = true
        publish()
    }

    fun cancelDelete() {
        deleting = false
        publish()
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        val shown = measure?.takeIf { deleting } ?: return
        writes.launch {
            val now = clock.now()
            measures.upsert(shown.copy(deleted = true, updatedAt = now))
            values.forEach { measurements.upsert(it.copy(deleted = true, updatedAt = now)) }
            deleting = false
            onDeleted()
        }
    }

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
        mutableState.value =
            MeasureUi(
                name = shown.name,
                unit = shown.unit,
                period = period,
                points = shownPeriod.map { it.day to it.value },
                latest = values.firstOrNull()?.let { measureValue(it.value, shown.unit) },
                change =
                    if (first != null && last != null && first != last) {
                        measureDelta(last.value, first.value)
                    } else {
                        null
                    },
                history =
                    values.map {
                        HistoryRowUi(
                            it.day,
                            dayMonthLabel(it.day, today.year),
                            measureValue(it.value, shown.unit),
                        )
                    },
                editing = editing,
                deleting = deleting,
            )
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }
}
