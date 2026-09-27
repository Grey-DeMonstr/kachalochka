package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.ui.components.DISABLED_ALPHA
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.format.WEEKDAY_LABELS
import monster.greyde.kachalochka.ui.format.isoDate
import monster.greyde.kachalochka.ui.icons.PhosphorIcons

data class DayUi(
    val day: CalendarDay,
    val hasVisit: Boolean,
    val today: Boolean,
    val selected: Boolean,
    val enabled: Boolean,
)

/** The month as the grid draws it; days after [today] cannot be chosen. */
internal fun monthWeeks(
    month: CalendarMonth,
    visitDays: Set<CalendarDay>,
    today: CalendarDay,
    selected: CalendarDay,
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SquareIconButton(
            PhosphorIcons.ArrowLeft,
            "Предыдущий месяц",
            onPrevious,
            Modifier.testTag("month-previous"),
        )
        Text(
            title,
            modifier = Modifier.weight(1f).testTag("calendar-month"),
            fontSize = 19.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (canShowNext) {
            SquareIconButton(
                PhosphorIcons.ArrowRight,
                "Следующий месяц",
                onNext,
                Modifier.testTag("month-next"),
            )
        } else {
            // Balances the previous-month button, which keeps the title centred.
            Spacer(Modifier.size(50.dp))
        }
    }
}

@Composable
internal fun MonthGrid(
    weeks: List<List<DayUi?>>,
    onSelect: (CalendarDay) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            WEEKDAY_LABELS.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
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
    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (day.selected) {
                        Modifier.border(1.dp, colors.primary, CircleShape)
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
                fontSize = 15.sp,
                fontWeight = if (day.today) FontWeight.Medium else FontWeight.Normal,
                color = if (day.today) colors.secondary else colors.onBackground,
            )
            // Drawn on every day, so a visit mark never shifts the number.
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (day.hasVisit) colors.primary else Color.Transparent),
            )
        }
    }
}
