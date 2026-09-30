package monster.greyde.kachalochka.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.DragHandle
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.ReorderState
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.components.SquareToggleButton
import monster.greyde.kachalochka.ui.components.TextInput
import monster.greyde.kachalochka.ui.components.dragOutline
import monster.greyde.kachalochka.ui.components.rememberReorderState
import monster.greyde.kachalochka.ui.components.reorderItem
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.MachineThumbnail
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PlanFormScreen(
    planId: PlanId?,
    pickedMachineId: MachineId?,
    onPickedMachineConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddMachine: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel: PlanFormViewModel = koinViewModel { parametersOf(planId) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(pickedMachineId) {
        if (pickedMachineId != null) {
            viewModel.add(pickedMachineId)
            onPickedMachineConsumed()
        }
    }
    LaunchedEffect(state.done) { if (state.done) onDone() }
    val s = strings()
    Screen(
        s.plan,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        actions = {
            if (state.rows.size > 1) {
                SquareToggleButton(
                    PhosphorIcons.Equals,
                    s.reorder,
                    state.ordering,
                    { viewModel.toggleOrdering() },
                    Modifier.testTag("plan-reorder"),
                )
            }
            if (state.canDelete) PlanMenu(viewModel::askToDelete)
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(s.planName)
            TextInput(
                state.name,
                s.untitledPlan,
                viewModel::typeName,
                Modifier.testTag("plan-name"),
            )
            val order = rememberReorderState()
            SideEffect { order.retain(state.rows.size) }
            Column {
                state.rows.forEachIndexed { index, row ->
                    PlanMachineRow(
                        row,
                        index,
                        order,
                        state.ordering,
                        onRemove = { viewModel.remove(row.id) },
                        onDrop = viewModel::move,
                    )
                }
            }
            if (!state.ordering) {
                OutlineButton(
                    s.addMachine,
                    PhosphorIcons.Plus,
                    onAddMachine,
                    Modifier.fillMaxWidth().testTag("plan-add-machine"),
                )
            }
        }
        Box(Modifier.padding(16.dp)) {
            AccentButton(
                s.savePlan,
                PhosphorIcons.Check,
                viewModel::save,
                Modifier.testTag("save-plan"),
                enabled = state.canSave,
            )
        }
    }
    if (state.confirmingDelete) {
        ConfirmDialog(
            title = s.deletePlanTitle,
            text = s.deletePlanText,
            confirmLabel = s.delete,
            confirmTag = "confirm-delete-plan",
            cancelTag = "cancel-delete-plan",
            onConfirm = viewModel::confirmDelete,
            onCancel = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun PlanMachineRow(
    row: PlanMachineUi,
    index: Int,
    order: ReorderState,
    ordering: Boolean,
    onRemove: () -> Unit,
    onDrop: (from: Int, to: Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val id = row.id.value
    Column(Modifier.reorderItem(order, index).dragOutline(order.dragging == index)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("plan-machine-$id"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ordering) DragHandle(order, index, "drag-plan-machine-$id", onDrop)
            MachineThumbnail(row.photo, PhosphorIcons.Barbell, size = 44.dp)
            Text(
                row.name,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                color = colors.onBackground,
            )
            if (!ordering) {
                SquareIconButton(
                    PhosphorIcons.Trash,
                    strings().unplan,
                    onRemove,
                    Modifier.testTag("remove-$id"),
                )
            }
        }
        Rule()
    }
}

@Composable
private fun PlanMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SquareIconButton(
            PhosphorIcons.DotsThreeVertical,
            strings().more,
            { expanded = true },
            Modifier.testTag("plan-menu"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(strings().deletePlan) },
                onClick = {
                    expanded = false
                    onDelete()
                },
                modifier = Modifier.testTag("delete-plan"),
            )
        }
    }
}
