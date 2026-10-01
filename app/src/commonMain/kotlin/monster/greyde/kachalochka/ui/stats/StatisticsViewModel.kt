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
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.bestPerDay
import monster.greyde.kachalochka.core.domain.gym.bestSet
import monster.greyde.kachalochka.core.domain.gym.machineProgress
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.gym.weightGain
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.preferredUnit
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.setsSummary
import monster.greyde.kachalochka.ui.format.shownLabel
import monster.greyde.kachalochka.ui.format.shownWeight
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.OwnMachines
import monster.greyde.kachalochka.ui.machine.ShownMachines
import monster.greyde.kachalochka.ui.strings.AppStrings
import kotlin.time.Clock
import kotlin.time.Instant

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

/** A null [selected] is "Общая", whose cards are [overall]. */
data class StatisticsUiState(
    val choices: List<StatsChoiceUi> = emptyList(),
    val selected: StatsChoiceUi? = null,
    val period: StatsPeriod = StatsPeriod.Month,
    val overallTitle: String = "",
    val overall: List<ProgressCardUi> = emptyList(),
    val machine: MachineStatsUi? = null,
)

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
    private var own: OwnMachines? = null
    private var setsByMachine: Map<MachineId, List<WorkoutSet>> = emptyMap()
    private var preferred = PreferredWeightUnit.Kg
    private var loading: Job? = null

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
                setsByMachine = sets.all(owner).groupBy { it.machineId }
                preferred = profiles.preferredUnit(owner)
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

    private fun publish() {
        val read = own ?: return
        val shown = ShownMachines(read, null)
        val now = clock.now()
        val today = CalendarDay.of(now, utcOffset.at(now))
        val start = period.start(today)
        val startAt = start.at(0L, utcOffset.at(now))
        val machine = read.machines.firstOrNull { it.id == selected }
        val periodLabel = periodLabel(start, today)
        mutableState.value =
            StatisticsUiState(
                choices = read.machines.map { StatsChoiceUi(it.id, it.name, shown.cover(it.id)) },
                selected = machine?.let { StatsChoiceUi(it.id, it.name, shown.cover(it.id)) },
                period = period,
                overallTitle = "${AppStrings.current.machinesInPeriod} · $periodLabel",
                overall =
                    if (machine == null) {
                        read.machines
                            .mapNotNull { card(it, shown, startAt, start, today) }
                            .sortedByDescending { it.second }
                            .map { it.first }
                    } else {
                        emptyList()
                    },
                machine = machine?.let { machineStats(it, start, today, periodLabel) },
            )
    }

    private fun periodLabel(
        start: CalendarDay,
        today: CalendarDay,
    ): String = AppStrings.current.sinceDay(dayMonthLabel(start, today.year))

    /** The card with the instant of its machine's last set, which orders the cards. */
    private fun card(
        machine: Machine,
        shown: ShownMachines,
        startAt: Instant,
        start: CalendarDay,
        today: CalendarDay,
    ): Pair<ProgressCardUi, Instant>? {
        val onMachine = setsByMachine[machine.id].orEmpty()
        val progress = machineProgress(onMachine, machine.weightMode, startAt) ?: return null
        val strings = AppStrings.current
        val (from, to) = progress.from to progress.to
        val (change, improved) = change(machine, from, to)
        val card =
            ProgressCardUi(
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
        return card to onMachine.maxOf { it.recordedAt }
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
