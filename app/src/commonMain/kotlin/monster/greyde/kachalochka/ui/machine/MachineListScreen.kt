package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MachineListScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMachine: (MachineId) -> Unit,
    onNewMachine: () -> Unit,
) {
    val viewModel: MachineListViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Screen("Тренажёры", onBack = onBack, onOpenSettings = onOpenSettings) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val rows = state
            when {
                rows == null -> Unit
                rows.isEmpty() ->
                    Text(
                        "Тренажёров пока нет",
                        modifier = Modifier.padding(16.dp).testTag("machine-list-empty"),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                else ->
                    rows.forEach { row ->
                        MachineRow(row.name, row.detail, "machine-list-row-${row.id.value}") {
                            onOpenMachine(row.id)
                        }
                    }
            }
        }
        Box(Modifier.padding(16.dp)) {
            AccentButton(
                "Новый тренажёр",
                PhosphorIcons.Plus,
                onNewMachine,
                Modifier.testTag("new-machine"),
            )
        }
    }
}
