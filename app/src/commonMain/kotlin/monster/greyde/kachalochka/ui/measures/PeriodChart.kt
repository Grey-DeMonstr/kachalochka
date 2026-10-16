package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.ChoiceRow
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.strings.strings

private fun periodLabel(period: MeasurePeriod): String =
    when (period) {
        MeasurePeriod.Month -> AppStrings.current.periodMonth
        MeasurePeriod.Quarter -> AppStrings.current.periodQuarter
        MeasurePeriod.HalfYear -> AppStrings.current.periodHalfYear
        MeasurePeriod.Year -> AppStrings.current.periodYear
        MeasurePeriod.All -> AppStrings.current.periodAll
    }

/**
 * The latest value, its change over the period and the day it was measured. Without a value,
 * [missing] tells what keeps one from being there. [cycleDay] goes beside the value.
 */
@Composable
internal fun MeasureSummary(
    latest: String?,
    change: String?,
    lastMeasured: String?,
    missing: String? = null,
    cycleDay: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    if (latest == null && missing != null) {
        Text(
            missing,
            Modifier.testTag("measure-latest"),
            fontSize = 17.sp,
            color = colors.onBackground.copy(alpha = 0.55f),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                latest ?: strings().noValues,
                Modifier.testTag("measure-latest"),
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onBackground,
            )
            change?.let {
                Text(
                    it,
                    Modifier.padding(bottom = 4.dp).testTag("measure-change"),
                    fontSize = 15.sp,
                    color = colors.onBackground.copy(alpha = 0.55f),
                )
            }
            cycleDay?.let {
                Spacer(Modifier.weight(1f))
                CycleDayChip(it, Modifier.padding(bottom = 4.dp))
            }
        }
        lastMeasured?.let {
            Text(
                it,
                Modifier.testTag("measure-last"),
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun CycleDayChip(
    text: String,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.primary.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.DropFill, null, Modifier.size(14.dp), tint = colors.primary)
        Text(
            text,
            Modifier.testTag("cycle-chip"),
            fontSize = 13.sp,
            color = colors.onBackground,
        )
    }
}

@Composable
internal fun PeriodChoice(
    period: MeasurePeriod,
    onChoose: (MeasurePeriod) -> Unit,
) {
    val periods = MeasurePeriod.entries
    ChoiceRow(
        choices = periods.map { Choice(periodLabel(it), "period-${it.name}") },
        selected = periods.indexOf(period),
        onSelect = { onChoose(periods[it]) },
    )
}

/**
 * Weekly values give a quarter about a dozen points; fewer than two draw no line. [phasesOnly]
 * draws the [phases] alone, without the values.
 */
@Composable
internal fun PeriodChart(
    points: List<Pair<CalendarDay, Double>>,
    format: (Double) -> String,
    phases: CyclePhasesUi? = null,
    phasesOnly: Boolean = false,
) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        when {
            phasesOnly && phases != null ->
                CycleChart(phases, Modifier.fillMaxSize().testTag("cycle-chart"))
            phasesOnly || points.isEmpty() ->
                Text(strings().noValuesInPeriod, fontSize = 15.sp, color = muted)
            points.size == 1 ->
                Text(
                    format(points.single().second),
                    Modifier.testTag("measure-single-value"),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            else -> MeasureChart(points, phases, Modifier.fillMaxSize().testTag("measure-chart"))
        }
    }
    if (phases != null && (phasesOnly || points.size > 1)) PhasesLegend()
}
