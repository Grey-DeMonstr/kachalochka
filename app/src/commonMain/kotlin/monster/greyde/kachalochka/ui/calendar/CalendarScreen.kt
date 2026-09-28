package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVisit: (CalendarDay) -> Unit,
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
                current.monthTitle,
                current.canShowNextMonth,
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
            current.visit?.let { visit ->
                VisitCard(
                    visit,
                    editable = !current.moving,
                    onOpen = { onOpenVisit(current.day) },
                    onMove = { viewModel.startMove(visit.id) },
                    onRemove = { viewModel.askToRemove(visit.id) },
                )
            }
            if (current.noVisit) {
                Text(
                    "Нет визита",
                    modifier = Modifier.testTag("calendar-empty"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                AccentButton(
                    "Добавить визит",
                    PhosphorIcons.Plus,
                    { onOpenVisit(current.day) },
                    Modifier.testTag("add-visit"),
                )
            }
        }
        current.removal?.let {
            ConfirmDialog(
                it.title,
                it.text,
                "Удалить",
                "confirm-remove",
                "cancel-remove",
                onConfirm = viewModel::confirmRemoval,
                onCancel = viewModel::cancelRemoval,
            )
        }
        current.replacement?.let {
            ConfirmDialog(
                it.title,
                it.text,
                "Заменить",
                "confirm-replace",
                "cancel-replace",
                onConfirm = viewModel::confirmReplacement,
                onCancel = viewModel::cancelReplacement,
            )
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
            .border(1.dp, colors.onBackground.copy(alpha = 0.16f), shape)
            .padding(12.dp),
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
                OutlineButton(
                    "Перенести",
                    PhosphorIcons.CalendarBlank,
                    onMove,
                    Modifier.weight(1f).testTag("move-visit-${visit.id.value}"),
                )
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
