package monster.greyde.kachalochka.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.friends.namesForViewer
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.keptVisit
import monster.greyde.kachalochka.core.domain.gym.summarize
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.calendar.DayUi
import monster.greyde.kachalochka.ui.calendar.monthRank
import monster.greyde.kachalochka.ui.calendar.monthWeeks
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.machineCount
import monster.greyde.kachalochka.ui.format.monthTitle
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.weekdayName
import monster.greyde.kachalochka.ui.machine.visibleLinks
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

data class FriendCalendarUiState(
    val monthTitle: String,
    val canShowNextMonth: Boolean,
    val weeks: List<List<DayUi?>>,
    val day: CalendarDay,
    val dayTitle: String,
    val visit: FriendDayUi?,
    val noVisit: Boolean,
)

data class FriendDayUi(
    val counts: String,
    val machines: String?,
)

class FriendCalendarViewModel(
    private val member: UserId,
    private val friends: FriendsRepository,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val machineLinks: MachineLinkRepository,
    private val colors: FriendColorStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FriendCalendarUiState?>(null)
    val state: StateFlow<FriendCalendarUiState?> = mutableState
    private val mutableOffline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = mutableOffline

    /** The friend's dated visits. */
    private var all: List<Visit> = emptyList()
    private val visitDays: Set<CalendarDay> get() = all.mapNotNull { it.day }.toSet()
    private var names: Map<MachineId, String> = emptyMap()
    private var color: Int? = null
    private var stale = true
    private var dayCard: FriendDayUi? = null
    private var month: CalendarMonth = CalendarMonth.of(today())
    private var selected: CalendarDay = today()
    private var reloading: Job? = null

    init {
        viewModelScope.launch {
            accounts.activeId.collect { reload() }
        }
        viewModelScope.launch { AppStrings.flow.drop(1).collect { reload() } }
    }

    fun refresh() = reload()

    fun showMonth(direction: Int) {
        val next = month.plusMonths(direction)
        if (monthRank(next) > monthRank(CalendarMonth.of(today()))) return
        month = next
        publish()
    }

    fun selectDay(day: CalendarDay) {
        if (day > today()) return
        selected = day
        dayCard = null
        publish()
        reload(everything = false)
    }

    /**
     * Cancels the reload in flight, so one for a previous day or account never lands last. A
     * day-only reload still reads everything when a cancelled one had not finished doing so.
     */
    private fun reload(everything: Boolean = true) {
        if (everything) stale = true
        reloading?.cancel()
        reloading = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        reading {
            if (stale) {
                all = friends.visits(member).filter { it.day != null }
                val theirs = friends.machines(member)
                val me = currentUser.id()
                val links = me?.let { visibleLinks(it, friends, machineLinks) }.orEmpty()
                names = namesForViewer(theirs, machines.all(me), MachineClusters(links))
                color = me?.let { colors.colorsFor(it, listOf(member))[member] }
                stale = false
            }
            val day = selected
            val sameDay = all.filter { it.day == day }
            dayCard =
                if (sameDay.isEmpty()) {
                    null
                } else {
                    val setsByVisit =
                        if (sameDay.size > 1) {
                            sameDay.associate { it.id to friends.sets(it) }
                        } else {
                            emptyMap()
                        }
                    val visit = keptVisit(sameDay) { setsByVisit[it]?.isNotEmpty() == true }
                    val visitSets = setsByVisit[visit.id] ?: friends.sets(visit)
                    val summary = summarize(visitSets)
                    val machineNames = groupByMachine(visitSets).mapNotNull { names[it.machineId] }
                    val machinesLabel = machineCount(summary.machineCount)
                    val setsLabel = setCount(summary.setCount)
                    FriendDayUi(
                        counts = "$machinesLabel · $setsLabel",
                        machines = machineNames.joinToString(", ").ifEmpty { null },
                    )
                }
        }.onSuccess {
            publish()
            mutableOffline.value = false
        }.onFailure {
            mutableOffline.value = true
        }
    }

    private fun publish() {
        val today = today()
        val day = selected
        val marked = visitDays
        mutableState.value =
            FriendCalendarUiState(
                monthTitle = monthTitle(month),
                canShowNextMonth = monthRank(month) < monthRank(CalendarMonth.of(today)),
                // The friend's visits wear the colour chosen for them, as in the own calendar.
                weeks =
                    color?.let { chosen ->
                        monthWeeks(
                            month,
                            emptySet(),
                            today,
                            day,
                            marked.associateWith { listOf(chosen) },
                        )
                    } ?: monthWeeks(month, marked, today, day),
                day = day,
                dayTitle = "${weekdayName(day.dayOfWeek)}, ${dayMonthLabel(day, today.year)}",
                visit = dayCard,
                noVisit = day !in marked,
            )
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }
}
