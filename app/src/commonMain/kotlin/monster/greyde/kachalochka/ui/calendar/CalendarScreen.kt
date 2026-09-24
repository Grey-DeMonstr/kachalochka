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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.DISABLED_ALPHA
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.format.WEEKDAY_LABELS
import monster.greyde.kachalochka.ui.format.isoDate
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVisit: (VisitId) -> Unit,
) {
    val viewModel: CalendarViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val current = state
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = current?.moving == true,
        onBackCompleted = { viewModel.cancelMove() },
    )
    Screen(
        "Визиты",
        onBack = { if (!viewModel.cancelMove()) onBack() },
        onOpenSettings = onOpenSettings,
    ) {
        if (current == null) return@Screen
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthHeader(
                current,
                onPrevious = { viewModel.showMonth(-1) },
                onNext = { viewModel.showMonth(+1) },
            )
            if (current.moving) MoveBanner(onCancel = { viewModel.cancelMove() })
            MonthGrid(current.weeks, onSelect = viewModel::selectDay)
            Rule()
            Text(
                current.dayTitle,
                modifier = Modifier.testTag("calendar-day-title"),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            current.visits.forEach { visit ->
                VisitCard(
                    visit,
                    editable = !current.moving,
                    onOpen = { onOpenVisit(visit.id) },
                    onMove = { viewModel.startMove(visit.id) },
                    onRemove = { viewModel.askToRemove(visit.id) },
                )
            }
            if (current.noVisits) {
                Text(
                    "Нет визитов",
                    modifier = Modifier.testTag("calendar-empty"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
            current.addLabel?.let {
                AccentButton(
                    it,
                    PhosphorIcons.Plus,
                    { viewModel.addVisit(onOpenVisit) },
                    Modifier.testTag("add-visit"),
                )
            }
        }
        current.removal?.let {
            RemovalDialog(
                it,
                onConfirm = viewModel::confirmRemoval,
                onCancel = viewModel::cancelRemoval,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    state: CalendarUiState,
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
            state.monthTitle,
            modifier = Modifier.weight(1f).testTag("calendar-month"),
            fontSize = 19.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (state.canShowNextMonth) {
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
private fun MoveBanner(onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().testTag("move-banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Выберите новый день",
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            color = colors.secondary,
        )
        Text(
            "Отмена",
            modifier = Modifier.clickable(onClick = onCancel).padding(8.dp).testTag("cancel-move"),
            fontSize = 15.sp,
            color = colors.onBackground,
        )
    }
}

@Composable
private fun MonthGrid(
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

@Composable
private fun VisitCard(
    visit: CalendarVisitUi,
    editable: Boolean,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                1.dp,
                if (visit.running) colors.primary else colors.onBackground.copy(alpha = 0.16f),
                shape,
            ).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(ControlShape)
                .clickable(onClick = onOpen)
                .padding(4.dp)
                .testTag("calendar-visit-${visit.id.value}"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (visit.running) {
                Text(
                    "ВИЗИТ ИДЁТ",
                    fontSize = 11.sp,
                    letterSpacing = 0.09.em,
                    color = colors.secondary,
                )
            }
            Text(visit.counts, fontSize = 15.sp, color = colors.onBackground)
            visit.machines?.let {
                Text(
                    it,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        if (editable) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!visit.running) {
                    OutlineButton(
                        "Перенести",
                        PhosphorIcons.CalendarBlank,
                        onMove,
                        Modifier.weight(1f).testTag("move-visit-${visit.id.value}"),
                    )
                }
                OutlineButton(
                    "Удалить",
                    PhosphorIcons.Trash,
                    onRemove,
                    Modifier.weight(1f).testTag("remove-visit-${visit.id.value}"),
                )
            }
        }
    }
}

@Composable
private fun RemovalDialog(
    removal: RemovalUi,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(removal.title) },
        text = { Text(removal.text) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-remove")) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-remove")) {
                Text("Отмена")
            }
        },
    )
}
