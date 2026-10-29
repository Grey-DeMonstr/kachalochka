package monster.greyde.kachalochka.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.gym.PriorBestsKeeper
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
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.dayAt
import monster.greyde.kachalochka.core.domain.gym.keptVisit
import monster.greyde.kachalochka.core.domain.gym.removedVisit
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.account.accountAvatar
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.account.traineeActive
import monster.greyde.kachalochka.ui.format.DeviceClock
import monster.greyde.kachalochka.ui.format.monthTitle
import monster.greyde.kachalochka.ui.format.weekdayDate
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.plans.Placement
import monster.greyde.kachalochka.ui.strings.AppStrings

/** How many days the feed reads at a time. */
private const val WINDOW_DAYS = 21L

data class FeedUiState(
    val days: List<FeedDayUi>,
    val loading: Boolean,
    /** No visit is older than the last day read. */
    val ended: Boolean,
    val removal: RemovalUi? = null,
    /** The account first, then its friends; empty on a device nobody has signed in on. */
    val people: List<FeedPersonUi> = emptyList(),
    val nobodyShown: Boolean = false,
)

data class FeedPersonUi(
    val person: PersonUi,
    /** A friend's calendar colour; null for the account. */
    val color: Int?,
    val shown: Boolean,
)

data class FeedDayUi(
    val day: CalendarDay,
    /** The month's name and year, over the first day shown of each month. */
    val month: String?,
    val title: String,
    /** The date beside "Сегодня" and "Вчера". */
    val aside: String?,
    val cards: List<VisitCardUi>,
)

/**
 * The own and the friends' visits, newest day first. Own rows are local and shown at once;
 * friends' are read online [WINDOW_DAYS] days at a time, and a failed read leaves them out.
 */
class VisitFeedViewModel(
    private val visits: VisitRepository,
    private val sets: WorkoutSetRepository,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: DeviceClock,
    private val placement: Placement,
    private val sync: SyncTrigger,
    private val friends: FriendsRepository,
    private val colors: FriendColorStore,
    private val avatars: AccountAvatars,
    private val profiles: ProfileRepository,
    private val people: FeedPeoplePreference,
    private val priorBests: PriorBestsKeeper,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FeedUiState?>(null)
    val state: StateFlow<FeedUiState?> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var owner: UserId? = null
    private var own: List<Visit> = emptyList()
    private var setsByVisit: Map<VisitId, List<WorkoutSet>> = emptyMap()
    private var machinesById: Map<MachineId, Machine> = emptyMap()
    private var history = OwnHistory(emptyList(), emptyMap())
    private var preferred = PreferredWeightUnit.Kg

    /** The oldest day read so far; the day after today before the first read. */
    private var from: CalendarDay = clock.today().plusDays(1)
    private var loading = false
    private var paging: Job? = null

    /** The friends' oldest visit day: null until read, a failure once a friends' read fails. */
    private var friendsEnd: Result<CalendarDay?>? = null
    private var friendVisits: List<FriendVisit> = emptyList()
    private var friendColors: Map<UserId, Int> = emptyMap()
    private var asked: Set<Pair<UserId, CalendarDay>> = emptySet()
    private var details: Map<Pair<UserId, CalendarDay>, FriendDayDetails> = emptyMap()
    private var theirMachines: Map<UserId, Deferred<Map<MachineId, Machine>>> = emptyMap()
    private var removing: Visit? = null
    private var mates: List<Friend> = emptyList()
    private var hidden: Set<HiddenPerson> = people.hidden.value

    private val friendsOnline: Boolean get() = friendsEnd?.isSuccess != false && owner != null

    private val ownShown: Boolean get() = owner?.let(::shows) ?: true

    private val nobodyShown: Boolean
        get() {
            val viewer = owner ?: return false
            return (listOf(viewer) + mates.map { it.userId }).none(::shows)
        }

    /** A shown visit is read, or none is left to read. */
    private val filled: Boolean
        get() =
            ended ||
                nobodyShown ||
                (ownShown && own.any { checkNotNull(it.day) >= from }) ||
                friendVisits.any { shows(it.friend.userId) && checkNotNull(it.visit.day) >= from }

    private val ended: Boolean
        get() {
            val ownOldest = own.minOfOrNull { checkNotNull(it.day) }
            val friendsOldest = friendsEnd?.getOrNull()?.takeIf { friendsOnline }
            val oldest = listOfNotNull(ownOldest, friendsOldest).minOrNull() ?: return true
            return from <= oldest
        }

    /** The feed follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect {
                removing = null
                restart()
            }
        }
        viewModelScope.launch { sync.completed.collect { refresh() } }
    }

    /** Reads the own rows again, keeping the days read so far. */
    fun refresh() {
        val shown = owner
        viewModelScope.launch {
            readOwn(currentUser.id().takeIf { it == shown } ?: return@launch)
            publish()
        }
    }

    fun loadMore() {
        if (paging?.isActive == true || ended || nobodyShown) return
        val key = owner
        paging = viewModelScope.launch { page(key) }
    }

    /** Hides [person]'s cards, or shows them again, and keeps the choice on the device. */
    fun toggle(person: UserId) {
        val pair = HiddenPerson(owner ?: return, person)
        val kept = if (pair in hidden) hidden - pair else hidden + pair
        hidden = kept
        viewModelScope.launch { people.set(kept) }
        publish()
        if (!filled) loadMore()
    }

    // A trainer deletes and repeats none of a trainee's visits.
    fun askToRemove(id: VisitId) {
        if (accounts.traineeActive()) return
        removing = own.firstOrNull { it.id == id } ?: return
        publish()
    }

    fun cancelRemoval() {
        removing = null
        publish()
    }

    fun confirmRemoval() {
        val visit = removing ?: return
        writes.launch {
            val removed = removedVisit(visit, sets.forVisit(visit.id), clock.now())
            removed.sets.forEach { sets.upsert(it) }
            visits.upsert(removed.visit)
            priorBests.refresh(visit.userId, removed.sets.map { it.machineId }.toSet())
            removing = null
            sync.request()
            readOwn(owner)
            publish()
        }
    }

    /** Plans [day]'s machines in today's visit, then reports today. */
    fun repeatToday(
        day: CalendarDay,
        onDone: (CalendarDay) -> Unit,
    ) {
        if (accounts.traineeActive()) return
        val active = accounts.activeId.value
        writes.launch { placement.repeatToday(active, day)?.let(onDone) }
    }

    private fun restart() {
        paging?.cancel()
        paging =
            viewModelScope.launch {
                val reader = currentUser.id()
                owner = reader
                from = clock.today().plusDays(1)
                friendsEnd = if (reader == null) Result.success(null) else null
                friendVisits = emptyList()
                friendColors = emptyMap()
                asked = emptySet()
                details = emptyMap()
                theirMachines = emptyMap()
                mates = emptyList()
                readOwn(reader)
                page(reader)
                if (reader != null && friendsOnline) readPeople(reader)
            }
    }

    private suspend fun readOwn(reader: UserId?) {
        val read = visits.all(reader).map { it.copy(day = it.dayAt(clock::offsetAt)) }
        val ownSets = sets.all(reader)
        if (currentUser.id() != reader) return
        own = read
        setsByVisit = ownSets.groupBy { it.visitId }.mapValues { it.value.sortedWith(visitOrder) }
        machinesById = machines.all(reader).associateBy { it.id }
        history = OwnHistory(ownSets, read.associate { it.id to checkNotNull(it.day) })
        preferred = profiles.preferredUnit(reader)
    }

    /** Reads windows back until one holds a visit or none is older. */
    private suspend fun page(reader: UserId?) {
        do {
            val to = from.plusDays(-1)
            val start = to.plusDays(1 - WINDOW_DAYS)
            from = start
            loading = true
            publish()
            if (reader != null && friendsOnline) readFriends(reader, start, to)
            if (currentUser.id() != reader) return
            val found =
                (ownShown && own.any { checkNotNull(it.day) in start..to }) ||
                    friendVisits.any {
                        shows(it.friend.userId) && checkNotNull(it.visit.day) in start..to
                    }
        } while (!found && !ended && !nobodyShown)
        loading = false
        publish()
        if (reader != null) readDetails(reader)
    }

    /** Reads the friends for the people row, then reads on if they are all the feed shows. */
    private suspend fun readPeople(reader: UserId) {
        val read =
            catching {
                val found =
                    friends
                        .mates(reader)
                        .sortedWith(compareBy({ it.displayName.lowercase() }, { it.userId.value }))
                found to colors.colorsFor(reader, found.map { it.userId })
            }.getOrNull() ?: return
        if (currentUser.id() != reader) return
        mates = read.first
        friendColors = friendColors + read.second
        publish()
        if (!filled) page(reader)
    }

    private fun shows(person: UserId): Boolean =
        owner?.let { HiddenPerson(it, person) !in hidden } ?: true

    private suspend fun readFriends(
        reader: UserId,
        start: CalendarDay,
        to: CalendarDay,
    ) {
        val read =
            catching {
                if (friendsEnd == null) {
                    val oldest = friends.oldestGroupVisit(reader)
                    friendsEnd = Result.success(oldest?.dayAt(clock::offsetAt))
                }
                val window =
                    friends
                        .groupVisits(reader, start, to)
                        .map {
                            it.copy(
                                visit = it.visit.copy(day = it.visit.dayAt(clock::offsetAt)),
                            )
                        }.filter { checkNotNull(it.visit.day) in start..to }
                window to colors.colorsFor(reader, window.map { it.friend.userId })
            }
        read.onFailure { friendsEnd = Result.failure(it) }
        val (window, found) = read.getOrNull() ?: return
        if (currentUser.id() != reader) return
        friendVisits = friendVisits + window
        friendColors = friendColors + found
    }

    /** Reads each friend's day not read yet, all at once, publishing each as it lands. */
    private fun readDetails(reader: UserId) {
        val pending =
            friendVisits
                .groupBy { it.friend.userId to checkNotNull(it.visit.day) }
                .filterKeys { it !in asked }
        asked = asked + pending.keys
        pending.forEach { (key, theirs) ->
            viewModelScope.launch {
                val found =
                    catching {
                        val machines = machinesOf(key.first).await()
                        friends.dayDetails(theirs.map { it.visit }, machines, preferred)
                    }.getOrNull() ?: return@launch
                if (owner != reader) return@launch
                details = details + (key to found)
                publish()
            }
        }
    }

    /** Each friend's machines are read once, however many of their days the feed shows. */
    private fun machinesOf(friend: UserId): Deferred<Map<MachineId, Machine>> =
        theirMachines[friend] ?: viewModelScope
            .async { friends.machines(friend).associateBy { it.id } }
            .also { theirMachines = theirMachines + (friend to it) }

    private fun publish() {
        val today = clock.today()
        val shownOwn =
            own
                .filter { ownShown && checkNotNull(it.day) >= from }
                .groupBy { checkNotNull(it.day) }
        val shownFriends =
            friendVisits
                .filter { shows(it.friend.userId) && checkNotNull(it.visit.day) >= from }
                .groupBy { checkNotNull(it.visit.day) }
        var lastMonth: CalendarMonth? = null
        val days =
            (shownOwn.keys + shownFriends.keys).sortedDescending().map { day ->
                val month = CalendarMonth.of(day)
                val label = monthTitle(month).takeIf { month != lastMonth }
                lastMonth = month
                FeedDayUi(
                    day = day,
                    month = label,
                    title = dayTitle(day, today),
                    aside = dayAside(day, today),
                    cards =
                        listOfNotNull(ownCardOn(day, shownOwn[day].orEmpty(), today)) +
                            friendCardsOn(day, shownFriends[day].orEmpty()),
                )
            }
        mutableState.value =
            FeedUiState(
                days = days,
                loading = loading,
                ended = ended,
                removal = removing?.let { removalUi(it, today) },
                people = peopleUi(),
                nobodyShown = nobodyShown,
            )
    }

    private fun peopleUi(): List<FeedPersonUi> {
        val viewer = owner ?: return emptyList()
        val friendsRow =
            mates.mapNotNull { mate ->
                val color = friendColors[mate.userId] ?: return@mapNotNull null
                val person = PersonUi(mate.userId, mate.displayName, mate.avatar)
                FeedPersonUi(person, color, shows(mate.userId))
            }
        return listOf(FeedPersonUi(ownPerson(), null, shows(viewer))) + friendsRow
    }

    private fun ownCardOn(
        day: CalendarDay,
        sameDay: List<Visit>,
        today: CalendarDay,
    ): VisitCardUi? {
        if (sameDay.isEmpty()) return null
        val visit = keptVisit(sameDay) { setsByVisit[it].orEmpty().isNotEmpty() }
        return ownCard(
            visit,
            setsByVisit[visit.id].orEmpty(),
            day,
            today,
            ownPerson(),
            machinesById,
            history,
            preferred,
        ).copy(tag = "feed-visit-${visit.id.value}")
    }

    private fun friendCardsOn(
        day: CalendarDay,
        sameDay: List<FriendVisit>,
    ): List<VisitCardUi> =
        sameDay
            .groupBy { it.friend.userId }
            .values
            .map { it.first().friend }
            .sortedWith(compareBy({ it.displayName.lowercase() }, { it.userId.value }))
            .mapNotNull { friend ->
                val color = friendColors[friend.userId] ?: return@mapNotNull null
                val tag = "feed-friend-${friend.userId.value}-${day.iso}"
                friendCard(tag, friend, color, day, details[friend.userId to day])
            }

    private fun ownPerson(): PersonUi {
        val account = accounts.accounts.value.firstOrNull { it.userId == owner }
        return account?.let {
            PersonUi(it.userId, it.shownName, accountAvatar(it, avatars.photos.value))
        } ?: PersonUi(null, AppStrings.current.me, Avatar())
    }
}

private fun dayTitle(
    day: CalendarDay,
    today: CalendarDay,
): String =
    when (day) {
        today -> AppStrings.current.todayTitle
        today.plusDays(-1) -> AppStrings.current.yesterday
        else -> weekdayDate(day, withYear = day.year != today.year)
    }

private fun dayAside(
    day: CalendarDay,
    today: CalendarDay,
): String? {
    if (day != today && day != today.plusDays(-1)) return null
    val strings = AppStrings.current
    return "${strings.weekdayInline(day.dayOfWeek)}, ${strings.dayMonth(day.day, day.month)}"
}
