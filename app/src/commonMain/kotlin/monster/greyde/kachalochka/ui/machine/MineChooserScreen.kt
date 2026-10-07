package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** "Привязать к моему": the account's machine to link the friend's [machineId] to. */
@Composable
fun MineChooserScreen(
    machineId: MachineId,
    name: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onLinked: () -> Unit,
) {
    val viewModel: MineChooserViewModel = koinViewModel { parametersOf(machineId, name) }
    val state by viewModel.state.collectAsState()
    Screen(strings().linkToMine, onBack = onBack, onOpenSettings = onOpenSettings) {
        SearchBar(state.query, viewModel::onQueryChange)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.own.isEmpty()) {
                Text(
                    strings().nothingFound,
                    modifier = Modifier.padding(16.dp).testTag("mine-nothing"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            } else {
                SectionLabel(
                    strings().myMachines,
                    modifier =
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            state.own.forEach { row ->
                MachineRow(
                    row.name,
                    row.detail,
                    "mine-${row.id.value}",
                    row.photo,
                    row.linkedWith,
                ) { viewModel.choose(row.id, onLinked) }
            }
        }
    }
}
