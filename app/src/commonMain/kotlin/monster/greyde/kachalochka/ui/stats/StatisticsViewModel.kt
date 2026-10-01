package monster.greyde.kachalochka.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.MachineProgress
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.core.domain.gym.StatsSort
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.bestPerDay
import monster.greyde.kachalochka.core.domain.gym.bestSet
import monster.greyde.kachalochka.core.domain.gym.machinePeaks
import monster.greyde.kachalochka.core.domain.gym.machineProgress
import monster.greyde.kachalochka.core.domain.gym.statsOrder
import monster.greyde.kachalochka.core.domain.gym.tagSections
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.gym.weightGain
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.ProfileChoice
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.setsSummary
import monster.greyde.kachalochka.ui.format.shownLabel
import monster.greyde.kachalochka.ui.format.shownWeight
import monster.greyde.kachalochka.ui.format.tagTitle
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.OwnMachines
import monster.greyde.kachalochka.ui.machine.ShownMachines
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock

data class StatsChoiceUi(
    val id: MachineId,
    val name: String,
    val photo: Photo?,
)

/** A machine's change over the period, from its [from] set to its [to] set. */
data class ProgressCardUi(
    val id: MachineId,
    val name: String,
    val photo: Photo?,
    val fromLabel: String,
    val from: String,
    val toLabel: String,
    val to: String,
    val change: String,
    val improved: Boolean,
)

data class HistoryRowUi(
    val date: String,
    val results: String,
)

/** [points] are each day's best weight, negative on a gravitron, over [start]..[end]. */
data class MachineStatsUi(
    val title: String,
    val best: String?,
    val points: List<Pair<CalendarDay, Double>>,
    val start: CalendarDay,
    val end: CalendarDay,
    val zeroOnTop: Boolean,
    val history: List<HistoryRowUi>,
)

/** A run of [items] under the tags in [title]; without grouping, one section titled null. */
data class StatsSectionUi<T>(
    val title: String?,
    val items: List<T>,
)

/** A null [selected] is "Общая", whose cards are [overall]. */
data class StatisticsUiState(
    val choiceSections: List<StatsSectionUi<StatsChoiceUi>> = emptyList(),
    val selected: StatsChoiceUi? = null,
    val period: StatsPeriod = StatsPeriod.Month,
    val sort: StatsSort = StatsSort.Recent,
    val groupByTag: Boolean = false,
    /** True when a machine of the account's has a tag. */
    val canGroupByTag: Boolean = false,
    val overallTitle: String = "",
    val overallSections: List<StatsSectionUi<ProgressCardUi>> = emptyList(),
    val machine: MachineStatsUi? = null,
) {
    val choices: List<StatsChoiceUi> get() = choiceSections.flatMap { it.items }
    val overall: List<ProgressCardUi> get() = overallSections.flatMap { it.items }
}

class StatisticsViewModel(
    initial: MachineId?,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val sync: SyncTrigger,
    private val catalogue: MachineCatalogue,
    private val sets: WorkoutSetRepository,
    private val profiles: ProfileRepository,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
) : ViewModel() {
    private val mutableState = MutableStateFlow(StatisticsUiState())
    val state: StateFlow<StatisticsUiState> = mutableState

    private var selected: MachineId? = initial
    private var period = StatsPeriod.Month
    private var sort = StatsSort.Recent
    private var own: OwnMachines? = null
    private var setsByMachine: Map<MachineId, List<WorkoutSet>> = emptyMap()
    private var peaks: Map<MachineId, MachinePeaks> = emptyMap()
    private var preferred = PreferredWeightUnit.Kg
    private var loading: Job? = null

    /** The account's own choice, which the visit screen shares. */
    private val grouping =
        ProfileChoice(
            profiles,
            currentUser,
            clock,
            sync,
            viewModelScope,
            stored = { it?.groupByTag ?: false },
            written = { profile, grouped -> profile.copy(groupByTag = grouped) },
        )

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
        viewModelScope.launch { sync.completed.collect { load() } }
    }

    fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                own = catalogue.own(owner)
                val all = sets.all(owner)
                setsByMachine = all.groupBy { it.machineId }
                peaks = machinePeaks(all).associateBy { it.machineId }
                preferred = profiles.preferredUnit(owner)
                grouping.read(owner)
                publish()
            }
    }

    /** A null [machine] is "Общая". */
    fun choose(machine: MachineId?) {
        selected = machine
        publish()
    }

    fun choosePeriod(chosen: StatsPeriod) {
        period = chosen
        publish()
    }

    fun chooseSort(chosen: StatsSort) {
        sort = chosen
        publish()
    }

    fun toggleGroupByTag() {
        grouping.choose(!grouping.current)
        publish()
    }

    private fun publish() {
        val read = own ?: return
        val shown = ShownMachines(read, null)
        val now = clock.now()
        val today = CalendarDay.of(now, utcOffset.at(now))
        val start = period.start(today)
        val startAt = start.at(0L, utcOffset.at(now))
        val machine = read.machines.firstOrNull { it.id == selected }
        val periodLabel = periodLabel(start, today)
        val progress =
            read.machines
                .mapNotNull { m ->
                    machineProgress(setsByMachine[m.id].orEmpty(), m.weightMode, startAt)
                        ?.let { m.id to it }
                }.toMap()
        val ordered = read.machines.sortedWith(statsOrder(sort, peaks, progress))

        fun choice(it: Machine) = StatsChoiceUi(it.id, it.name, shown.cover(it.id))
        mutableState.value =
            StatisticsUiState(
                choiceSections = sections(ordered, ::choice),
                selected = machine?.let(::choice),
                period = period,
                sort = sort,
                groupByTag = grouping.current,
                canGroupByTag = read.machines.any { it.tags.isNotEmpty() },
                overallTitle = "${AppStrings.current.machinesInPeriod} · $periodLabel",
                overallSections =
                    if (machine == null) {
                        sections(ordered.filter { it.id in progress }) {
                            card(it, shown, progress.getValue(it.id), start, today)
                        }
                    } else {
                        emptyList()
                    },
                machine = machine?.let { machineStats(it, start, today, periodLabel) },
            )
    }

    /** [machines] keep their order; grouped, they run in [tagSections] as the visit's do. */
    private fun <T> sections(
        machines: List<Machine>,
        item: (Machine) -> T,
    ): List<StatsSectionUi<T>> =
        when {
            machines.isEmpty() -> emptyList()
            grouping.current ->
                tagSections(machines) { it.tags }.map { section ->
                    StatsSectionUi(
                        section.tags.takeIf { it.isNotEmpty() }?.let(::tagTitle),
                        section.items.map(item),
                    )
                }
            else -> listOf(StatsSectionUi(null, machines.map(item)))
        }

    private fun periodLabel(
        start: CalendarDay,
        today: CalendarDay,
    ): String = AppStrings.current.sinceDay(dayMonthLabel(start, today.year))

    private fun card(
        machine: Machine,
        shown: ShownMachines,
        progress: MachineProgress,
        start: CalendarDay,
        today: CalendarDay,
    ): ProgressCardUi {
        val strings = AppStrings.current
        val (from, to) = progress.from to progress.to
        val (change, improved) = change(machine, from, to)
        return ProgressCardUi(
            id = machine.id,
            name = machine.name,
            photo = shown.cover(machine.id),
            fromLabel =
                if (progress.sinceBefore) {
                    strings.beforeDay(dayMonthLabel(start, today.year))
                } else {
                    strings.worst
                },
            from = setValue(from.weight, from.reps, machine, preferred),
            toLabel =
                if (progress.sinceBefore) {
                    periodLabel(start, today).replaceFirstChar { it.uppercase() }
                } else {
                    strings.best
                },
            to = setValue(to.weight, to.reps, machine, preferred),
            change = change,
            improved = improved,
        )
    }

    /** Weight first; at the same shown weight, the reps. */
    private fun change(
        machine: Machine,
        from: WorkoutSet,
        to: WorkoutSet,
    ): Pair<String, Boolean> {
        val gain =
            weightGain(
                shownWeight(from.weight, machine, preferred),
                shownWeight(to.weight, machine, preferred),
                machine.weightMode,
            )
        val reps = to.reps - from.reps
        val strings = AppStrings.current
        val unit = shownLabel(machine, preferred)
        return when {
            gain > 0 -> "+${formatNumber(gain)} $unit" to true
            gain < 0 -> "−${formatNumber(-gain)} $unit" to false
            reps > 0 -> strings.moreReps(reps) to true
            reps < 0 -> strings.fewerReps(-reps) to false
            else -> strings.noChange to false
        }
    }

    private fun machineStats(
        machine: Machine,
        start: CalendarDay,
        today: CalendarDay,
        periodLabel: String,
    ): MachineStatsUi {
        val onMachine = setsByMachine[machine.id].orEmpty()
        val strings = AppStrings.current
        val counterweight = machine.weightMode == WeightMode.Counterweight
        val inPeriod =
            bestPerDay(onMachine, machine.weightMode, utcOffset::at).filter { it.first >= start }
        val best = bestSet(inPeriod.map { it.second }, machine.weightMode)
        val sign = if (counterweight) -1 else 1
        return MachineStatsUi(
            title =
                listOfNotNull(
                    strings.bestSetTitle,
                    strings.counterweightTitle.takeIf { counterweight },
                    periodLabel,
                ).joinToString(" · "),
            best = best?.let { setValue(it.weight, it.reps, machine, preferred) },
            points =
                inPeriod.map { (day, set) ->
                    day to sign * shownWeight(set.weight, machine, preferred)
                },
            start = start,
            end = today,
            zeroOnTop = counterweight,
            history =
                onMachine
                    .groupBy { it.visitId }
                    .values
                    .sortedByDescending { visit -> visit.maxOf { it.recordedAt } }
                    .map { visit ->
                        val first = visit.minOf { it.recordedAt }
                        HistoryRowUi(
                            dayMonthLabel(CalendarDay.of(first, utcOffset.at(first)), today.year),
                            setsSummary(machine, visit.sortedWith(visitOrder), preferred),
                        )
                    },
        )
    }
}
