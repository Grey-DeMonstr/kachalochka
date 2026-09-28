package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.DragHandle
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.ReorderState
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.components.dragOutline
import monster.greyde.kachalochka.ui.components.rememberReorderState
import monster.greyde.kachalochka.ui.components.reorderItem
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MeasuresScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewMeasurement: () -> Unit,
) {
    val viewModel: MeasuresViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Screen("Замеры", onBack = onBack, onOpenSettings = onOpenSettings) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Показатели", Modifier.weight(1f))
                Text(
                    if (state.ordering) "Готово" else "Порядок",
                    Modifier
                        .clickable(onClick = viewModel::toggleOrdering)
                        .padding(8.dp)
                        .testTag("reorder-toggle"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            val order = rememberReorderState()
            SideEffect { order.retain(state.rows.size) }
            state.rows.forEachIndexed { index, row ->
                MeasureRow(row, index, order, state.ordering) { from, to ->
                    viewModel.move(state.rows[from].id, to)
                }
            }
        }
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentButton(
                "Новый замер",
                PhosphorIcons.Plus,
                onNewMeasurement,
                Modifier.testTag("new-measurement"),
            )
            OutlineButton(
                "Добавить показатель",
                PhosphorIcons.Ruler,
                viewModel::openAdd,
                Modifier.fillMaxWidth().testTag("add-measure"),
            )
        }
    }
    state.adding?.let {
        NewMeasureDialog(
            it,
            onName = viewModel::typeName,
            onUnit = viewModel::typeUnit,
            onConfirm = viewModel::confirmAdd,
            onCancel = viewModel::dismissAdd,
        )
    }
}

@Composable
private fun MeasureRow(
    row: MeasureRowUi,
    index: Int,
    order: ReorderState,
    ordering: Boolean,
    onDrop: (from: Int, to: Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.55f)
    val id = row.id.value
    Column(Modifier.reorderItem(order, index).dragOutline(order.dragging == index)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = if (ordering) 4.dp else 12.dp)
                .padding(end = if (ordering) 12.dp else 0.dp)
                .testTag("measure-row-$id"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ordering) DragHandle(order, index, "drag-measure-$id", onDrop)
            Column(Modifier.weight(1f)) {
                Text(row.name, fontSize = 16.sp, color = colors.onBackground)
                row.ago?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("measure-ago-$id"),
                        fontSize = 13.sp,
                        color = muted,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                row.value?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("measure-value-$id"),
                        fontSize = 16.sp,
                        color = colors.onBackground,
                    )
                }
                row.delta?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("measure-delta-$id"),
                        fontSize = 13.sp,
                        color = muted,
                    )
                }
            }
        }
        Rule()
    }
}

@Composable
private fun NewMeasureDialog(
    adding: NewMeasureUi,
    onName: (String) -> Unit,
    onUnit: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Новый показатель") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = adding.name,
                    onValueChange = onName,
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.testTag("new-measure-name"),
                )
                OutlinedTextField(
                    value = adding.unit,
                    onValueChange = onUnit,
                    label = { Text("Единица, например см") },
                    singleLine = true,
                    modifier = Modifier.testTag("new-measure-unit"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = adding.canSave,
                modifier = Modifier.testTag("confirm-new-measure"),
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-new-measure")) {
                Text("Отмена")
            }
        },
    )
}
