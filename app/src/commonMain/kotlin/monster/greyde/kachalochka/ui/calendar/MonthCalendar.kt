package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.DISABLED_ALPHA
import monster.greyde.kachalochka.ui.format.isoDate
import monster.greyde.kachalochka.ui.format.weekdayLabels
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.friendColor

data class DayUi(
    val day: CalendarDay,
    val hasVisit: Boolean,
    val today: Boolean,
    val selected: Boolean,
    val enabled: Boolean,
    /** Palette indices of the friends who trained that day, one each. */
    val friendDots: List<Int> = emptyList(),
)

private const val MAX_DOTS = 4

/** The month as the grid draws it; days after [today] cannot be chosen. */
internal fun monthWeeks(
    month: CalendarMonth,
    visitDays: Set<CalendarDay>,
    today: CalendarDay,
    selected: CalendarDay,
    friendDots: Map<CalendarDay, List<Int>> = emptyMap(),
): List<List<DayUi?>> =
    month.weeks().map { week ->
        week.map { day ->
            day?.let {
                DayUi(
                    day = it,
                    hasVisit = it in visitDays,
                    today = it == today,
                    selected = it == selected,
                    enabled = it <= today,
                    friendDots = friendDots[it].orEmpty(),
                )
            }
        }
    }

internal fun monthRank(month: CalendarMonth): Int = month.year * 12 + month.month

@Composable
internal fun MonthHeader(
    title: String,
    canShowNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CaretButton(
            PhosphorIcons.CaretLeft,
            strings().previousMonth,
            onPrevious,
            Modifier.testTag("month-previous"),
        )
        Text(
            title,
            modifier = Modifier.weight(1f).testTag("calendar-month"),
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        CaretButton(
            PhosphorIcons.CaretRight,
            strings().nextMonth,
            onNext,
            Modifier.testTag("month-next"),
            enabled = canShowNext,
        )
    }
}

@Composable
private fun CaretButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(ControlShape)
            .border(1.dp, colors.onBackground.copy(alpha = 0.16f), ControlShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = colors.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun MonthGrid(
    weeks: List<List<DayUi?>>,
    onSelect: (CalendarDay) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            weekdayLabels().forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    color = colors.onBackground.copy(alpha = 0.45f),
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Box(Modifier.weight(1f))
                    } else {
                        DayCell(day, onClick = { onSelect(day.day) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    day: DayUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // A fixed height keeps the grid short on wide web windows.
    Box(Modifier.weight(1f).height(50.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .then(
                    if (day.selected) {
                        Modifier
                            .background(colors.primary.copy(alpha = 0.14f))
                            .border(1.5.dp, colors.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ).alpha(if (day.enabled) 1f else DISABLED_ALPHA)
                .clickable(enabled = day.enabled, onClick = onClick)
                .testTag("day-${isoDate(day.day)}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                day.day.day.toString(),
                fontSize = 16.sp,
                fontWeight = if (day.today) FontWeight.Medium else FontWeight.Normal,
                color =
                    when {
                        day.selected -> colors.onPrimaryContainer
                        day.today -> colors.secondary
                        else -> colors.onBackground
                    },
            )
            // Drawn on every day, so a visit mark never shifts the number.
            Row(
                Modifier.padding(top = 4.dp).height(5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (day.hasVisit) Dot(colors.primary)
                val iso = isoDate(day.day)
                day.friendDots.take(MAX_DOTS - if (day.hasVisit) 1 else 0).forEachIndexed { n, i ->
                    Dot(friendColor(i), Modifier.testTag("day-dot-friend-$iso-$n"))
                }
            }
        }
    }
}

@Composable
private fun Dot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(5.dp).clip(CircleShape).background(color))
}
