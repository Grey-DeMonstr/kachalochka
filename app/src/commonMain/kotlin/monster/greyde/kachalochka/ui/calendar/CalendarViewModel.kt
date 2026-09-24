package monster.greyde.kachalochka.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRows
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.movedVisit
import monster.greyde.kachalochka.core.domain.gym.pastVisit
import monster.greyde.kachalochka.core.domain.gym.removedVisit
import monster.greyde.kachalochka.core.domain.gym.summarize
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.machineCount
import monster.greyde.kachalochka.ui.format.monthTitle
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.weekdayName
import kotlin.time.Clock

data class CalendarUiState(
    val monthTitle: String,
    val canShowNextMonth: Boolean,
    val weeks: List<List<DayUi?>>,
    val dayTitle: String,
    val visits: List<CalendarVisitUi>,
    val addLabel: String?,
    val moving: Boolean,
    val removal: RemovalUi?,
)

data class DayUi(
    val day: CalendarDay,
    val hasVisit: Boolean,
    val today: Boolean,
    val selected: Boolean,
    val enabled: Boolean,
)

data class CalendarVisitUi(
    val id: VisitId,
    val running: Boolean,
    val counts: String,
    val machines: String?,
)

data class RemovalUi(
    val title: String,
    val text: String,
)

class CalendarViewModel(
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CalendarUiState?>(null)
    val state: StateFlow<CalendarUiState?> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var all: List<Visit> = emptyList()
    private var activeId: VisitId? = null
    private var dayVisits: List<CalendarVisitUi> = emptyList()
    private var month: CalendarMonth? = CalendarMonth.of(today())
    private var selected: CalendarDay? = today()
    private var moving: Visit? = null
    private var removing: Visit? = null
    private var removingSets: Int = 0

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                moving = null
                removing = null
                reload()
            }
        }
        viewModelScope.launch { sync.completed.collect { reload() } }
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun showMonth(direction: Int) {
        val next = (month ?: CalendarMonth.of(today())).plusMonths(direction)
        if (monthRank(next) > monthRank(CalendarMonth.of(today()))) return
        month = next
        publish()
    }

    fun selectDay(day: CalendarDay) {
        if (day > today()) return
        val target = moving
        if (target == null) {
            selected = day
            viewModelScope.launch { reload() }
        } else {
            writes.launch {
                val now = clock.now()
                val offset = utcOffset.at(now)
                write(movedVisit(target, sets.forVisit(target.id), day, offset, now))
                moving = null
                selected = day
                month = CalendarMonth.of(day)
                sync.request()
                reload()
            }
        }
    }

    fun addVisit(onOpen: (VisitId) -> Unit) {
        writes.launch {
            val day = selected ?: today()
            val owner = currentUser.id()
            val now = clock.now()
            val id =
                if (day < today()) {
                    val visit = pastVisit(day, owner, utcOffset.at(now), now)
                    visits.upsert(visit)
                    sync.request()
                    visit.id
                } else {
                    val active = visits.active(owner)
                    if (active != null) {
                        active.id
                    } else {
                        val visit = Visit(VisitId.random(), owner, now, null, now, false)
                        visits.upsert(visit)
                        sync.request()
                        visit.id
                    }
                }
            onOpen(id)
        }
    }

    fun startMove(id: VisitId) {
        val visit = all.firstOrNull { it.id == id } ?: return
        if (visit.endedAt == null) return
        moving = visit
        publish()
    }

    fun cancelMove(): Boolean {
        if (moving == null) return false
        moving = null
        publish()
        return true
    }

    fun askToRemove(id: VisitId) {
        val visit = all.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            removingSets = sets.forVisit(visit.id).size
            removing = visit
            publish()
        }
    }

    fun cancelRemoval() {
        removing = null
        publish()
    }

    fun confirmRemoval() {
        val visit = removing ?: return
        writes.launch {
            val now = clock.now()
            write(removedVisit(visit, sets.forVisit(visit.id), now))
            removing = null
            sync.request()
            reload()
        }
    }

    private suspend fun write(rows: VisitRows) {
        rows.sets.forEach { sets.upsert(it) }
        visits.upsert(rows.visit)
    }

    private suspend fun reload() {
        val owner = currentUser.id()
        all = visits.all(owner)
        activeId = visits.active(owner)?.id
        val machinesById = machines.all(owner).associateBy { it.id }
        val day = selected ?: today()
        dayVisits =
            all
                .filter { CalendarDay.of(it.recordedAt, utcOffset.at(it.recordedAt)) == day }
                .sortedBy { it.recordedAt }
                .map { visit ->
                    val visitSets = sets.forVisit(visit.id)
                    val summary = summarize(visitSets)
                    val names =
                        groupByMachine(visitSets).mapNotNull { machinesById[it.machineId]?.name }
                    val machinesLabel = machineCount(summary.machineCount)
                    val setsLabel = setCount(summary.setCount)
                    CalendarVisitUi(
                        id = visit.id,
                        running = visit.endedAt == null,
                        counts = "$machinesLabel · $setsLabel",
                        machines = names.joinToString(", ").ifEmpty { null },
                    )
                }
        publish()
    }

    private fun publish() {
        val today = today()
        val currentMonth = month ?: CalendarMonth.of(today)
        val day = selected ?: today
        val visitDays =
            all.map { CalendarDay.of(it.recordedAt, utcOffset.at(it.recordedAt)) }.toSet()
        val weeksUi =
            currentMonth.weeks().map { week ->
                week.map { d ->
                    d?.let {
                        DayUi(
                            day = it,
                            hasVisit = it in visitDays,
                            today = it == today,
                            selected = it == day,
                            enabled = it <= today,
                        )
                    }
                }
            }
        val addLabel =
            when {
                day < today -> "Добавить визит"
                day == today && activeId == null -> "Начать визит"
                else -> null
            }
        mutableState.value =
            CalendarUiState(
                monthTitle = monthTitle(currentMonth),
                canShowNextMonth = monthRank(currentMonth) < monthRank(CalendarMonth.of(today)),
                weeks = weeksUi,
                dayTitle = "${weekdayName(day.dayOfWeek)}, ${dayMonthLabel(day, today.year)}",
                visits = dayVisits,
                addLabel = addLabel,
                moving = moving != null,
                removal = removing?.let { removalUi(it, today) },
            )
    }

    private fun removalUi(
        visit: Visit,
        today: CalendarDay,
    ): RemovalUi {
        val day = CalendarDay.of(visit.recordedAt, utcOffset.at(visit.recordedAt))
        return RemovalUi(
            "Удалить визит?",
            "${dayMonthLabel(day, today.year)} · ${setCount(removingSets)}. " +
                "Подходы пропадут из истории и статистики.",
        )
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }

    private fun monthRank(month: CalendarMonth): Int = month.year * 12 + month.month
}
