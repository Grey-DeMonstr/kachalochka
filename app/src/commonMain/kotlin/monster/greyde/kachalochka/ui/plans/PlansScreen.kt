package monster.greyde.kachalochka.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlan: (PlanId?) -> Unit,
    onStarted: (CalendarDay) -> Unit,
) {
    val viewModel: PlansViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    val colors = MaterialTheme.colorScheme
    Screen(strings().plans, onBack = onBack, onOpenSettings = onOpenSettings) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (state.loaded && state.rows.isEmpty()) {
                Text(
                    strings().noPlansYet,
                    modifier = Modifier.padding(16.dp).testTag("no-plans"),
                    fontSize = 15.sp,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            }
            state.rows.forEach { row ->
                PlanRow(
                    row,
                    onOpen = { onOpenPlan(row.id) },
                    onStart = { viewModel.askToStart(row.id) },
                )
            }
            OutlineButton(
                strings().newPlan,
                PhosphorIcons.Plus,
                { onOpenPlan(null) },
                Modifier.fillMaxWidth().padding(16.dp).testTag("new-plan"),
            )
        }
    }
    if (state.confirmingStart != null) {
        ConfirmDialog(
            title = strings().startPlanTitle,
            text = strings().startPlanText,
            confirmLabel = strings().startPlan,
            confirmTag = "confirm-start-plan",
            cancelTag = "cancel-start-plan",
            onConfirm = { viewModel.start(onStarted) },
            onCancel = viewModel::cancelStart,
        )
    }
}

@Composable
private fun PlanRow(
    row: PlanRowUi,
    onOpen: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val id = row.id.value
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("plan-row-$id"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    modifier = Modifier.testTag("plan-title-$id"),
                    fontSize = 17.sp,
                    color = colors.onBackground,
                )
                Text(
                    row.count,
                    modifier = Modifier.testTag("plan-count-$id"),
                    fontSize = 13.sp,
                    color = colors.onBackground.copy(alpha = 0.52f),
                )
            }
            OutlineButton(
                strings().startPlan,
                PhosphorIcons.ArrowRight,
                onStart,
                Modifier.width(128.dp).testTag("start-plan-$id"),
            )
        }
        Rule()
    }
}
