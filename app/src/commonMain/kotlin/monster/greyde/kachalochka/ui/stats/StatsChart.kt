package monster.greyde.kachalochka.ui.stats

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.multiplatform.common.data.ExtraStore
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.measures.chartDayLabel
import monster.greyde.kachalochka.ui.measures.chartLabelSpacing
import kotlin.math.ceil
import kotlin.math.floor

private val valueLabel = CartesianValueFormatter { _, y, _ -> formatNumber(y) }

private val dayLabel =
    CartesianValueFormatter { _, x, _ -> chartDayLabel(CalendarDay.ofEpochDay(x.toLong())) }

/**
 * The whole period across, so a lone visit sits where it fell. The values are hugged as on the
 * measure chart, except that a gravitron's negative weights keep zero on top.
 */
private class PeriodRange(
    private val start: Double,
    private val end: Double,
    private val zeroOnTop: Boolean,
) : CartesianLayerRangeProvider {
    override fun getMinX(
        minX: Double,
        maxX: Double,
        extraStore: ExtraStore,
    ): Double = start

    override fun getMaxX(
        minX: Double,
        maxX: Double,
        extraStore: ExtraStore,
    ): Double = end

    override fun getMinY(
        minY: Double,
        maxY: Double,
        extraStore: ExtraStore,
    ): Double = floor(minY) - if (floor(minY) == ceil(maxY)) 1 else 0

    override fun getMaxY(
        minY: Double,
        maxY: Double,
        extraStore: ExtraStore,
    ): Double = if (zeroOnTop) 0.0 else ceil(maxY) + if (floor(minY) == ceil(maxY)) 1 else 0
}

/** [points] need at least one day, oldest first; x is the epoch day so gaps keep their width. */
@Composable
fun StatsChart(
    points: List<Pair<CalendarDay, Double>>,
    start: CalendarDay,
    end: CalendarDay,
    zeroOnTop: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val faint = Fill(colors.onBackground.copy(alpha = 0.10f))
    val label =
        rememberAxisLabelComponent(
            style = TextStyle(color = colors.onBackground.copy(alpha = 0.55f), fontSize = 11.sp),
        )
    val model =
        remember(points) {
            CartesianChartModel(
                LineCartesianLayerModel.build {
                    series(x = points.map { it.first.epochDay }, y = points.map { it.second })
                },
            )
        }
    val span = end.epochDay - start.epochDay
    val range =
        remember(start, end, zeroOnTop) {
            PeriodRange(start.epochDay.toDouble(), end.epochDay.toDouble(), zeroOnTop)
        }
    val chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(colors.primary)),
                            pointProvider =
                                LineCartesianLayer.PointProvider.single(
                                    LineCartesianLayer.Point(
                                        rememberShapeComponent(Fill(colors.primary), CircleShape),
                                        7.dp,
                                    ),
                                ),
                        ),
                    ),
                rangeProvider = range,
            ),
            startAxis =
                VerticalAxis.rememberStart(
                    line = null,
                    label = label,
                    valueFormatter = valueLabel,
                    tick = null,
                    guideline = rememberAxisGuidelineComponent(fill = faint),
                ),
            bottomAxis =
                HorizontalAxis.rememberBottom(
                    line = rememberAxisLineComponent(fill = faint),
                    label = label,
                    valueFormatter = dayLabel,
                    tick = null,
                    guideline = null,
                    itemPlacer =
                        remember(span) {
                            HorizontalAxis.ItemPlacer.aligned(
                                spacing = { chartLabelSpacing(span) },
                            )
                        },
                ),
            getXStep = { _, _, _ -> 1.0 },
        )
    CartesianChartHost(
        chart,
        model,
        modifier,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}
