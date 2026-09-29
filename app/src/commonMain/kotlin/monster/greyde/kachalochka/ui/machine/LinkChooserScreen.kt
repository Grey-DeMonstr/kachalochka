package monster.greyde.kachalochka.ui.machine

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
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LinkChooserScreen(
    machineId: MachineId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onMerged: (kept: MachineId) -> Unit,
    onLinked: () -> Unit,
) {
    val viewModel: LinkChooserViewModel = koinViewModel { parametersOf(machineId) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Screen(strings().linkTo, onBack = onBack, onOpenSettings = onOpenSettings) {
        SearchBar(state.query, viewModel::onQueryChange)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let {
                Text(
                    it,
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("chooser-error"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.own.isNotEmpty()) ChooserSection(strings().myMachines, "chooser-own")
            state.own.forEach { row ->
                MachineRow(row.name, row.detail, "chooser-own-${row.id.value}") {
                    viewModel.chooseOwn(row.id)
                }
            }
            state.friends?.takeIf { it.isNotEmpty() }?.let { rows ->
                ChooserSection(strings().friendsMachines, "chooser-friends")
                rows.forEach { row ->
                    MachineRow(row.name, row.detail, "chooser-friend-${row.id.value}") {
                        viewModel.chooseFriend(row.id, onLinked)
                    }
                }
            }
        }
    }
    state.merge?.let { merge ->
        ConfirmDialog(
            title = merge.title,
            text = merge.text,
            confirmLabel = strings().merge,
            confirmTag = "confirm-merge",
            cancelTag = "cancel-merge",
            onConfirm = { viewModel.confirmMerge(onMerged) },
            onCancel = viewModel::cancelMerge,
        )
    }
}

@Composable
private fun ChooserSection(
    title: String,
    tag: String,
) = SectionLabel(
    title,
    modifier =
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .testTag(tag),
)
