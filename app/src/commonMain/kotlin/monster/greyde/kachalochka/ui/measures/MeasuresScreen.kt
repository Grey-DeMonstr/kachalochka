package monster.greyde.kachalochka.ui.measures

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.measures.MeasureId
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
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MeasuresScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewMeasurement: () -> Unit,
    onOpenMeasure: (MeasureId) -> Unit,
) {
    val viewModel: MeasuresViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Screen(strings().measurements, onBack = onBack, onOpenSettings = onOpenSettings) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(strings().measuresSection, Modifier.weight(1f))
                Text(
                    if (state.ordering) strings().done else strings().reorder,
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
                MeasureRow(
                    row,
                    index,
                    order,
                    state.ordering,
                    { onOpenMeasure(row.id) },
                ) { from, to ->
                    viewModel.move(state.rows[from].id, to)
                }
            }
            FatSection(state.fat, state.profileIncomplete, onOpenSettings)
        }
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentButton(
                strings().newMeasurement,
                PhosphorIcons.Plus,
                onNewMeasurement,
                Modifier.testTag("new-measurement"),
            )
            OutlineButton(
                strings().addMeasure,
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
    onOpen: () -> Unit,
    onDrop: (from: Int, to: Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.55f)
    val id = row.id.value
    Column(Modifier.reorderItem(order, index).dragOutline(order.dragging == index)) {
        Row(
            Modifier
                .fillMaxWidth()
                // A clickable row would merge the drag handle's semantics into its own.
                .then(if (ordering) Modifier else Modifier.clickable(onClick = onOpen))
                .padding(vertical = if (ordering) 4.dp else 12.dp)
                .padding(end = if (ordering) 12.dp else 0.dp)
                .testTag("measure-row-$id"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ordering) DragHandle(order, index, "drag-measure-$id", onDrop)
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.name, fontSize = 16.sp, color = colors.onBackground)
                    row.tags.forEach { FormulaTag(it, Modifier.testTag("measure-tag-$id-$it")) }
                }
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
private fun FatSection(
    rows: List<FatRowUi>,
    profileIncomplete: Boolean,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.55f)
    SectionLabel(strings().bodyFat, Modifier.padding(top = 20.dp, bottom = 4.dp))
    rows.forEach { row ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .testTag("fat-${row.tag}"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormulaTag(row.tag)
            Text(row.name, Modifier.weight(1f), fontSize = 16.sp, color = colors.onBackground)
            Text(
                row.percent ?: row.missing ?: strings().cannotCalculate,
                Modifier.testTag("fat-value-${row.tag}"),
                fontSize = if (row.percent != null) 16.sp else 13.sp,
                color = if (row.percent != null) colors.onBackground else muted,
            )
        }
        Rule()
    }
    if (profileIncomplete) {
        Text(
            strings().fillProfile,
            Modifier
                .clickable(onClick = onOpenSettings)
                .padding(vertical = 12.dp)
                .testTag("fat-profile-link"),
            fontSize = 14.sp,
            color = colors.tertiary,
        )
    }
}

@Composable
private fun FormulaTag(
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        tag,
        modifier
            .border(1.dp, colors.tertiary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = colors.tertiary,
    )
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
        title = { Text(strings().newMeasure) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = adding.name,
                    onValueChange = onName,
                    label = { Text(strings().name) },
                    singleLine = true,
                    modifier = Modifier.testTag("new-measure-name"),
                )
                OutlinedTextField(
                    value = adding.unit,
                    onValueChange = onUnit,
                    label = { Text(strings().unitExample) },
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
                Text(strings().add)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-new-measure")) {
                Text(strings().cancel)
            }
        },
    )
}
