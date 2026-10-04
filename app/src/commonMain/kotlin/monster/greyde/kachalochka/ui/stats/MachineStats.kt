package monster.greyde.kachalochka.ui.stats

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.bestPerDay
import monster.greyde.kachalochka.core.domain.gym.bestSet
import monster.greyde.kachalochka.core.domain.gym.visitOrder
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.components.ChipRow
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.setValue
import monster.greyde.kachalochka.ui.format.setsSummary
import monster.greyde.kachalochka.ui.format.shownWeight
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.strings.strings

internal val BoxShape = RoundedCornerShape(8.dp)

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

/** "с 1 сентября", the period's first day as [today]'s year writes it. */
internal fun periodLabel(
    start: CalendarDay,
    today: CalendarDay,
): String = AppStrings.current.sinceDay(dayMonthLabel(start, today.year))

/**
 * One machine's statistics over [period]: the best set and each day's best weight since its
 * start, and every visit in [sets], newest first, in the viewer's unit.
 */
internal fun machineStatsUi(
    machine: Machine,
    sets: List<WorkoutSet>,
    period: StatsPeriod,
    today: CalendarDay,
    preferred: PreferredWeightUnit,
    utcOffset: UtcOffset,
): MachineStatsUi {
    val start = period.start(today)
    val strings = AppStrings.current
    val counterweight = machine.weightMode == WeightMode.Counterweight
    val inPeriod =
        bestPerDay(sets, machine.weightMode, utcOffset::at).filter { it.first >= start }
    val best = bestSet(inPeriod.map { it.second }, machine.weightMode)
    val sign = if (counterweight) -1 else 1
    return MachineStatsUi(
        title =
            listOfNotNull(
                strings.bestSetTitle,
                strings.counterweightTitle.takeIf { counterweight },
                periodLabel(start, today),
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
            sets
                .filterNot { it.deleted }
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

@Composable
internal fun PeriodChips(
    chosen: StatsPeriod,
    onChoose: (StatsPeriod) -> Unit,
) {
    val s = strings()
    ChipRow(
        listOf(
            StatsPeriod.Month to s.statsMonth,
            StatsPeriod.ThreeMonths to s.statsThreeMonths,
            StatsPeriod.SixMonths to s.statsSixMonths,
            StatsPeriod.Year to s.statsYear,
        ),
        chosen,
        onChoose,
    ) { "period-${it.name.lowercase()}" }
}

/** The period's best set over its chart, then every visit on the machine. */
@Composable
internal fun MachineStats(machine: MachineStatsUi) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel(machine.title, Modifier.weight(1f).testTag("stats-chart-title"))
        machine.best?.let {
            Text(
                it,
                modifier = Modifier.testTag("stats-best"),
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.7f),
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(BoxShape)
            .border(1.dp, colors.outlineVariant, BoxShape)
            .padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (machine.points.isEmpty()) {
            Muted(strings().noSetsInPeriod, Modifier.testTag("stats-empty"))
        } else {
            StatsChart(machine.points, machine.start, machine.end, machine.zeroOnTop)
        }
    }
    SectionLabel(strings().allResults, Modifier.padding(top = 24.dp, bottom = 8.dp))
    machine.history.forEachIndexed { i, row ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                row.date,
                modifier = Modifier.width(112.dp).testTag("stats-history-date-$i"),
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.55f),
            )
            Text(
                row.results,
                modifier = Modifier.testTag("stats-history-results-$i"),
                fontSize = 15.sp,
                color = colors.onBackground,
            )
        }
    }
}

@Composable
internal fun Muted(
    text: String,
    modifier: Modifier,
) {
    Text(
        text,
        modifier = modifier,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    )
}
