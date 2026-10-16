package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.ChoiceRow
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

/** The latest value, its change over the period and the day it was measured. */
@Composable
internal fun MeasureSummary(
    latest: String?,
    change: String?,
    lastMeasured: String?,
) {
    val colors = MaterialTheme.colorScheme
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

/** Weekly values give a quarter about a dozen points; fewer than two draw no line. */
@Composable
internal fun PeriodChart(
    points: List<Pair<CalendarDay, Double>>,
    format: (Double) -> String,
) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        when (points.size) {
            0 -> Text(strings().noValuesInPeriod, fontSize = 15.sp, color = muted)
            1 ->
                Text(
                    format(points.single().second),
                    Modifier.testTag("measure-single-value"),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            else -> MeasureChart(points, Modifier.fillMaxSize().testTag("measure-chart"))
        }
    }
}
