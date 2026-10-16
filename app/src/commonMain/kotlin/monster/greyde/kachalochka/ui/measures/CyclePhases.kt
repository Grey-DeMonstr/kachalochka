package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.measures.Cycle
import monster.greyde.kachalochka.core.domain.measures.CyclePhase
import monster.greyde.kachalochka.core.domain.measures.PhaseBand
import monster.greyde.kachalochka.core.domain.measures.bands
import monster.greyde.kachalochka.ui.strings.strings

/** The cycle's [bands] on a chart that runs from [first] to [last]. */
data class CyclePhasesUi(
    val first: CalendarDay,
    val last: CalendarDay,
    val bands: List<PhaseBand>,
)

fun cyclePhases(
    cycle: Cycle,
    first: CalendarDay,
    last: CalendarDay,
): CyclePhasesUi = CyclePhasesUi(first, last, cycle.bands(first, last))

internal class PhaseColors(
    val menstruation: Color,
    val ovulation: Color,
)

@Composable
internal fun phaseColors(): PhaseColors {
    val colors = MaterialTheme.colorScheme
    return PhaseColors(colors.primary.copy(alpha = 0.18f), colors.secondary.copy(alpha = 0.4f))
}

/** Menstruation fills its days, ovulation outlines them with a dashed line. */
internal fun DrawScope.drawPhase(
    phase: CyclePhase,
    area: Rect,
    colors: PhaseColors,
) {
    val corner = CornerRadius(3.dp.toPx())
    when (phase) {
        CyclePhase.Menstruation ->
            drawRoundRect(colors.menstruation, area.topLeft, area.size, corner)
        CyclePhase.Ovulation -> {
            val width = 1.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
            drawRoundRect(
                colors.ovulation,
                Offset(area.left + width / 2, area.top + width / 2),
                Size(area.width - width, area.height - width),
                corner,
                Stroke(width, pathEffect = dash),
            )
        }
    }
}

/** [xOf] places an epoch day's middle; each band covers its days in full over [area]'s height. */
internal fun DrawScope.drawPhases(
    bands: List<PhaseBand>,
    xOf: (Double) -> Float,
    area: Rect,
    colors: PhaseColors,
) = clipRect(area.left, area.top, area.right, area.bottom) {
    bands.forEach { band ->
        val left = xOf(band.first.epochDay - 0.5)
        val right = xOf(band.last.epochDay + 0.5)
        drawPhase(band.phase, Rect(left, area.top, right, area.bottom), colors)
    }
}

/** The phases alone, for the cycle day's own screen, with the first, middle and last days. */
@Composable
internal fun CycleChart(
    phases: CyclePhasesUi,
    modifier: Modifier = Modifier,
) {
    val colors = phaseColors()
    val text = MaterialTheme.colorScheme.onBackground
    val first = phases.first.epochDay
    val days = phases.last.epochDay - first + 1
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val dayWidth = size.width / days
            drawPhases(
                phases.bands,
                { epochDay -> ((epochDay - first + 0.5) * dayWidth).toFloat() },
                Rect(Offset.Zero, size),
                colors,
            )
            drawLine(
                text.copy(alpha = 0.10f),
                Offset(0f, size.height),
                Offset(size.width, size.height),
                1.dp.toPx(),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(first, first + days / 2, phases.last.epochDay).distinct().forEach {
                Text(
                    chartDayLabel(CalendarDay.ofEpochDay(it)),
                    fontSize = 11.sp,
                    color = text.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
internal fun PhasesLegend(modifier: Modifier = Modifier) {
    Row(
        modifier.testTag("cycle-legend"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(CyclePhase.Menstruation, strings().menstruation)
        LegendItem(CyclePhase.Ovulation, strings().ovulation)
    }
}

@Composable
private fun LegendItem(
    phase: CyclePhase,
    label: String,
) {
    val colors = phaseColors()
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(10.dp)) { drawPhase(phase, Rect(Offset.Zero, size), colors) }
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}
