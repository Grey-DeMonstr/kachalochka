package monster.greyde.kachalochka.ui.measures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.missingDefaults
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.daysAgoLabel
import kotlin.time.Clock

data class MeasureRowUi(
    val id: MeasureId,
    val name: String,
    val value: String?,
    val delta: String?,
    val ago: String?,
)

data class MeasuresUiState(
    val rows: List<MeasureRowUi>,
    val ordering: Boolean,
    val adding: NewMeasureUi?,
)

data class NewMeasureUi(
    val name: String,
    val unit: String,
    val canSave: Boolean,
)

class MeasuresViewModel(
    private val measures: MeasureRepository,
    private val measurements: MeasurementRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MeasuresUiState(emptyList(), false, null))
    val state: StateFlow<MeasuresUiState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    // An anonymous owner's seeds take random ids, so two loads seeding at once would each add
    // a full set.
    private val seeding = Mutex()
    private var shown: List<Measure> = emptyList()
    private var values: Map<MeasureId, List<Measurement>> = emptyMap()
    private var loading: Job? = null

    /** The list follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    /** Creates the predefined measures the owner has never had, then lists them all. */
    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                seed(owner)
                shown = measures.all(owner)
                values = measurements.all(owner).groupBy { it.measureId }
                publish()
            }
    }

    private suspend fun seed(owner: UserId?) =
        seeding.withLock {
            missingDefaults(owner, measures.kinds(owner)).forEach { measures.upsert(it) }
        }

    fun toggleOrdering() {
        mutableState.value = mutableState.value.copy(ordering = !mutableState.value.ordering)
    }

    fun move(
        id: MeasureId,
        index: Int,
    ) {
        writes.launch {
            val from = shown.indexOfFirst { it.id == id }
            if (from < 0) return@launch
            val order =
                shown.toMutableList().apply { add(index.coerceIn(0, lastIndex), removeAt(from)) }
            val now = clock.now()
            val changed =
                order
                    .withIndex()
                    .filter { (position, measure) -> measure.position != position }
                    .map { (position, measure) ->
                        measure.copy(position = position, updatedAt = now)
                    }.associateBy { it.id }
            // Shown before the write, so the dropped row stays where the finger left it.
            shown = order.map { changed[it.id] ?: it }
            publish()
            changed.values.forEach { measures.upsert(it) }
        }
    }

    fun openAdd() {
        mutableState.value = mutableState.value.copy(adding = NewMeasureUi("", "", false))
    }

    fun typeName(text: String) = editAdding { it.copy(name = text, canSave = text.isNotBlank()) }

    fun typeUnit(text: String) = editAdding { it.copy(unit = text) }

    private fun editAdding(change: (NewMeasureUi) -> NewMeasureUi) {
        val adding = mutableState.value.adding ?: return
        mutableState.value = mutableState.value.copy(adding = change(adding))
    }

    fun dismissAdd() {
        mutableState.value = mutableState.value.copy(adding = null)
    }

    fun confirmAdd() {
        val adding = mutableState.value.adding?.takeIf { it.canSave } ?: return
        writes.launch {
            val owner = currentUser.id()
            val last = measures.all(owner).maxOfOrNull { it.position } ?: -1
            measures.upsert(
                Measure(
                    id = MeasureId.random(),
                    userId = owner,
                    name = adding.name.trim(),
                    unit = adding.unit.trim(),
                    kind = null,
                    position = last + 1,
                    updatedAt = clock.now(),
                    deleted = false,
                ),
            )
            mutableState.value = mutableState.value.copy(adding = null)
            load()
        }
    }

    private fun publish() {
        val now = clock.now()
        val today = CalendarDay.of(now, utcOffset.at(now))
        mutableState.value =
            mutableState.value.copy(rows = shown.map { row(it, values[it.id].orEmpty(), today) })
    }

    /** [newestFirst] holds one value per day, as the repository reads them. */
    private fun row(
        measure: Measure,
        newestFirst: List<Measurement>,
        today: CalendarDay,
    ): MeasureRowUi {
        val latest = newestFirst.firstOrNull()
        val previous = newestFirst.getOrNull(1)
        return MeasureRowUi(
            id = measure.id,
            name = measure.name,
            value = latest?.let { measureValue(it.value, measure.unit) },
            delta =
                if (latest != null && previous != null) {
                    measureDelta(latest.value, previous.value)
                } else {
                    null
                },
            ago =
                latest?.let {
                    daysAgoLabel((today.epochDay - it.day.epochDay).toInt().coerceAtLeast(0))
                },
        )
    }
}
