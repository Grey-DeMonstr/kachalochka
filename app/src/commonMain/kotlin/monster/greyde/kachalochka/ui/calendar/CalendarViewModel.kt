package monster.greyde.kachalochka.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendVisit
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRows
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.allOn
import monster.greyde.kachalochka.core.domain.gym.dayAt
import monster.greyde.kachalochka.core.domain.gym.groupByMachine
import monster.greyde.kachalochka.core.domain.gym.keptVisit
import monster.greyde.kachalochka.core.domain.gym.movedVisit
import monster.greyde.kachalochka.core.domain.gym.removedVisit
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.summarize
import monster.greyde.kachalochka.core.domain.gym.visitRecency
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.machineCount
import monster.greyde.kachalochka.ui.format.monthTitle
import monster.greyde.kachalochka.ui.format.setCount
import monster.greyde.kachalochka.ui.format.weekdayName
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

data class CalendarUiState(
    val monthTitle: String,
    val canShowNextMonth: Boolean,
    val weeks: List<List<DayUi?>>,
    val day: CalendarDay,
    val dayTitle: String,
    val visit: CalendarVisitUi?,
    val noVisit: Boolean,
    val moving: Boolean,
    val removal: RemovalUi?,
    val replacement: ReplacementUi?,
    val friendVisits: List<FriendDayVisitUi>,
)

/** A friend's visit on the chosen day; [counts] stays null until their sets are read. */
data class FriendDayVisitUi(
    val userId: UserId,
    val name: String,
    val color: Int,
    val day: CalendarDay,
    val counts: String?,
)

data class CalendarVisitUi(
    val id: VisitId,
    val counts: String,
    val machines: String?,
)

data class RemovalUi(
    val title: String,
    val text: String,
)

data class ReplacementUi(
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
    private val friends: FriendsRepository,
    private val colors: FriendColorStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CalendarUiState?>(null)
    val state: StateFlow<CalendarUiState?> = mutableState
    private val writes = WriteGuard(viewModelScope)

    /** The owner's visits, each carrying the day it shows on. */
    private var all: List<Visit> = emptyList()
    private val visitDays: Set<CalendarDay> get() = all.mapNotNull { it.day }.toSet()
    private var machinesById: Map<MachineId, Machine> = emptyMap()
    private var stale = true
    private var dayCard: CalendarVisitUi? = null
    private var month: CalendarMonth = CalendarMonth.of(today())
    private var selected: CalendarDay = today()
    private var moving: Visit? = null
    private var removing: Visit? = null
    private var removingSets: Int = 0
    private var replacing: Visit? = null
    private var replacingSets: Int = 0
    private var reloading: Job? = null
    private var shownFor: UserId? = null

    /** Friends' visits around [friendsFor]'s month, each carrying the day it shows on. */
    private var friendVisits: List<FriendVisit> = emptyList()
    private var friendColors: Map<UserId, Int> = emptyMap()
    private var friendCounts: Map<Pair<UserId, CalendarDay>, String> = emptyMap()
    private var friendsFor: Pair<UserId, CalendarMonth>? = null
    private var loadingFriends: Job? = null
    private var countingFriends: Job? = null

    /** Friends read for another account or month than the one shown are never drawn. */
    private val shownFriends: List<FriendVisit>
        get() = friendVisits.takeIf { friendsFor != null && friendsFor == shownKey() }.orEmpty()

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                moving = null
                removing = null
                replacing = null
                reload()
            }
        }
        viewModelScope.launch { sync.completed.collect { reload() } }
        viewModelScope.launch { AppStrings.flow.drop(1).collect { publish() } }
    }

    fun refresh() = reload()

    fun showMonth(direction: Int) {
        val next = month.plusMonths(direction)
        if (monthRank(next) > monthRank(CalendarMonth.of(today()))) return
        month = next
        publish()
        loadFriends()
    }

    fun selectDay(day: CalendarDay) {
        if (day > today()) return
        val target = moving
        if (target == null || day == target.day) {
            moving = null
            replacing = null
            selected = day
            dayCard = null
            publish()
            reload(everything = false)
            countFriends()
            return
        }
        // A day holds one visit; moving onto an occupied one asks before replacing it.
        val occupant = all.filter { it.day == day }.maxWithOrNull(visitRecency)
        if (occupant != null) {
            viewModelScope.launch {
                replacingSets = sets.forVisit(occupant.id).size
                replacing = occupant
                publish()
            }
            return
        }
        writes.launch {
            // A sync may have removed the visit since the move began.
            val visit = visits.byId(target.id)?.takeIf { !it.deleted }
            if (visit == null) {
                moving = null
                replacing = null
                publish()
                return@launch
            }
            // The day marks may predate a sync that has since put a visit on the day.
            val arrived =
                visits
                    .shownOn(currentUser.id(), day, sets, utcOffset::at)
                    ?.takeIf { it.id != visit.id }
            if (arrived != null) {
                replacingSets = sets.forVisit(arrived.id).size
                replacing = arrived
                publish()
                return@launch
            }
            val now = clock.now()
            val offset = utcOffset.at(now)
            write(movedVisit(visit, sets.forVisit(visit.id), day, offset, now))
            moving = null
            replacing = null
            selected = day
            month = CalendarMonth.of(day)
            sync.request()
            reload()
        }
    }

    fun startMove(id: VisitId) {
        moving = all.firstOrNull { it.id == id } ?: return
        publish()
    }

    fun cancelMove(): Boolean {
        if (moving == null) return false
        moving = null
        replacing = null
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
            moving = null
            replacing = null
            sync.request()
            reload()
        }
    }

    fun cancelReplacement() {
        replacing = null
        publish()
    }

    fun confirmReplacement() {
        val target = moving ?: return
        val day = replacing?.day ?: return
        writes.launch {
            val visit = visits.byId(target.id)?.takeIf { !it.deleted }
            val now = clock.now()
            if (visit != null) {
                // Read at confirmation, so a visit pulled onto the day since the ask goes too.
                // Every one goes: normalization could otherwise keep another over the moved one.
                visits
                    .allOn(currentUser.id(), day, utcOffset::at)
                    .filter { it.id != visit.id }
                    .forEach { write(removedVisit(it, sets.forVisit(it.id), now)) }
                write(movedVisit(visit, sets.forVisit(visit.id), day, utcOffset.at(now), now))
                selected = day
                month = CalendarMonth.of(day)
                sync.request()
            }
            moving = null
            replacing = null
            reload()
        }
    }

    private suspend fun write(rows: VisitRows) {
        rows.sets.forEach { sets.upsert(it) }
        visits.upsert(rows.visit)
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
        if (stale) {
            val owner = currentUser.id()
            all = visits.all(owner).map { it.copy(day = it.dayAt(utcOffset::at)) }
            machinesById = machines.all(owner).associateBy { it.id }
            shownFor = owner
            stale = false
            loadFriends()
        }
        val day = selected
        val sameDay = all.filter { it.day == day }
        dayCard =
            if (sameDay.isEmpty()) {
                null
            } else {
                val setsByVisit =
                    if (sameDay.size > 1) {
                        sameDay.associate { it.id to sets.forVisit(it.id) }
                    } else {
                        emptyMap()
                    }
                val visit = keptVisit(sameDay) { setsByVisit[it]?.isNotEmpty() == true }
                val visitSets = setsByVisit[visit.id] ?: sets.forVisit(visit.id)
                val summary = summarize(visitSets)
                val names =
                    groupByMachine(visitSets).mapNotNull { machinesById[it.machineId]?.name }
                val machinesLabel = machineCount(summary.machineCount)
                val setsLabel = setCount(summary.setCount)
                CalendarVisitUi(
                    id = visit.id,
                    counts = "$machinesLabel · $setsLabel",
                    machines = names.joinToString(", ").ifEmpty { null },
                )
            }
        publish()
    }

    private fun shownKey(): Pair<UserId, CalendarMonth>? = shownFor?.let { it to month }

    /** Online and never stored; the own calendar never waits for it, and a failure shows none. */
    private fun loadFriends() {
        loadingFriends?.cancel()
        countingFriends?.cancel()
        val key = shownKey() ?: return
        val (owner, shownMonth) = key
        loadingFriends =
            viewModelScope.launch {
                val lastDay = CalendarDay(shownMonth.year, shownMonth.month, shownMonth.length)
                val found =
                    reading {
                        val placed =
                            friends.groupVisits(owner, shownMonth.first(), lastDay).map {
                                it.copy(visit = it.visit.copy(day = it.visit.dayAt(utcOffset::at)))
                            }
                        placed to colors.colorsFor(owner, placed.map { it.friend.userId })
                    }.getOrDefault(emptyList<FriendVisit>() to emptyMap())
                if (currentUser.id() != owner || shownKey() != key) return@launch
                friendCounts =
                    friendCounts.filterKeys { friendsFor == key && it.second == selected }
                friendVisits = found.first
                friendColors = found.second
                friendsFor = key
                publish()
                count(key, selected)
            }
    }

    private fun countFriends() {
        countingFriends?.cancel()
        val key = friendsFor?.takeIf { it == shownKey() } ?: return
        val day = selected
        countingFriends = viewModelScope.launch { count(key, day) }
    }

    /** Reads the sets of each friend's visit on [day], the one [keptVisit] would show. */
    private suspend fun count(
        key: Pair<UserId, CalendarMonth>,
        day: CalendarDay,
    ) {
        val byFriend = friendVisits.filter { it.visit.day == day }.groupBy { it.friend.userId }
        for ((friend, theirs) in byFriend) {
            val counts = reading { countsOf(theirs.map { it.visit }) }.getOrNull() ?: continue
            if (friendsFor != key) return
            friendCounts = friendCounts + ((friend to day) to counts)
            publish()
        }
    }

    private suspend fun countsOf(sameDay: List<Visit>): String {
        val setsByVisit =
            if (sameDay.size > 1) sameDay.associate { it.id to friends.sets(it) } else emptyMap()
        val visit = keptVisit(sameDay) { setsByVisit[it]?.isNotEmpty() == true }
        val summary = summarize(setsByVisit[visit.id] ?: friends.sets(visit))
        return "${machineCount(summary.machineCount)} · ${setCount(summary.setCount)}"
    }

    private fun friendsOn(day: CalendarDay): List<Friend> =
        shownFriends
            .filter { it.visit.day == day }
            .map { it.friend }
            .distinctBy { it.userId }
            .sortedWith(
                compareBy<Friend> { it.displayName.lowercase() }.thenBy { it.userId.value },
            )

    private fun publish() {
        val today = today()
        val day = selected
        val marked = visitDays
        val dots =
            shownFriends.mapNotNull { it.visit.day }.toSet().associateWith { friendDay ->
                friendsOn(friendDay).mapNotNull { friendColors[it.userId] }
            }
        mutableState.value =
            CalendarUiState(
                monthTitle = monthTitle(month),
                canShowNextMonth = monthRank(month) < monthRank(CalendarMonth.of(today)),
                weeks = monthWeeks(month, marked, today, day, dots),
                day = day,
                dayTitle = "${weekdayName(day.dayOfWeek)}, ${dayMonthLabel(day, today.year)}",
                visit = dayCard,
                // Read from the day marks, which are known while the day's card still loads.
                noVisit = day !in marked,
                moving = moving != null,
                removal = removing?.let { removalUi(it, today) },
                replacement = replacing?.let { replacementUi(it, today) },
                friendVisits =
                    friendsOn(day).mapNotNull { friend ->
                        val color = friendColors[friend.userId] ?: return@mapNotNull null
                        val counts = friendCounts[friend.userId to day]
                        FriendDayVisitUi(friend.userId, friend.displayName, color, day, counts)
                    },
            )
    }

    private fun removalUi(
        visit: Visit,
        today: CalendarDay,
    ): RemovalUi {
        val day = checkNotNull(visit.day)
        return RemovalUi(
            AppStrings.current.deleteVisitTitle,
            "${dayMonthLabel(day, today.year)} · ${setCount(removingSets)}. " +
                AppStrings.current.deleteVisitText,
        )
    }

    private fun replacementUi(
        occupant: Visit,
        today: CalendarDay,
    ): ReplacementUi {
        val day = occupant.day ?: today
        return ReplacementUi(
            AppStrings.current.replaceVisitTitle,
            AppStrings.current.replaceVisitText(
                dayMonthLabel(day, today.year),
                setCount(replacingSets),
            ),
        )
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }
}
