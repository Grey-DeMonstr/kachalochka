package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasurePeriod
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.ChoiceRow
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private fun periodLabel(period: MeasurePeriod): String =
    when (period) {
        MeasurePeriod.Month -> "1 мес"
        MeasurePeriod.Quarter -> "3 мес"
        MeasurePeriod.HalfYear -> "6 мес"
        MeasurePeriod.Year -> "Год"
        MeasurePeriod.All -> "Всё"
    }

@Composable
fun MeasureScreen(
    measureId: MeasureId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDay: (CalendarDay) -> Unit,
    onGone: () -> Unit,
) {
    val viewModel: MeasureViewModel = koinViewModel { parametersOf(measureId) }
    val state by viewModel.state.collectAsState()
    val gone by viewModel.gone.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(gone) { if (gone) onGone() }
    val measure = state
    Screen(
        measure?.name.orEmpty(),
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        actions = {
            if (measure != null) MeasureMenu(viewModel::openEdit, viewModel::askDelete)
        },
    ) {
        if (measure == null) return@Screen
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Summary(measure)
            val periods = MeasurePeriod.entries
            ChoiceRow(
                choices = periods.map { Choice(periodLabel(it), "period-${it.name}") },
                selected = periods.indexOf(measure.period),
                onSelect = { viewModel.choose(periods[it]) },
            )
            ChartArea(measure)
            SectionLabel("История")
            Column {
                measure.history.forEach { HistoryRow(it, onOpenDay) }
            }
        }
    }
    measure?.editing?.let {
        EditMeasureDialog(
            it,
            onName = viewModel::typeName,
            onUnit = viewModel::typeUnit,
            onConfirm = viewModel::confirmEdit,
            onCancel = viewModel::dismissEdit,
        )
    }
    if (measure?.deleting == true) {
        ConfirmDialog(
            title = "Удалить показатель?",
            text = "«${measure.name}» и все его значения пропадут.",
            confirmLabel = "Удалить",
            confirmTag = "confirm-delete-measure",
            cancelTag = "cancel-delete-measure",
            onConfirm = viewModel::confirmDelete,
            onCancel = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun Summary(measure: MeasureUi) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            measure.latest ?: "Нет значений",
            Modifier.testTag("measure-latest"),
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onBackground,
        )
        measure.change?.let {
            Text(
                it,
                Modifier.padding(bottom = 4.dp).testTag("measure-change"),
                fontSize = 15.sp,
                color = colors.onBackground.copy(alpha = 0.55f),
            )
        }
    }
}

/** Weekly values give a quarter about a dozen points; fewer than two draw no line. */
@Composable
private fun ChartArea(measure: MeasureUi) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        when (measure.points.size) {
            0 -> Text("За этот период значений нет", fontSize = 15.sp, color = muted)
            1 ->
                Text(
                    measureValue(measure.points.single().second, measure.unit),
                    Modifier.testTag("measure-single-value"),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            else -> MeasureChart(measure.points, Modifier.fillMaxSize().testTag("measure-chart"))
        }
    }
}

@Composable
private fun HistoryRow(
    row: HistoryRowUi,
    onOpenDay: (CalendarDay) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.clickable { onOpenDay(row.day) }.testTag("history-${row.day.iso}")) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.label, Modifier.weight(1f), fontSize = 16.sp, color = colors.onBackground)
            Text(row.value, fontSize = 16.sp, color = colors.onBackground)
        }
        Rule()
    }
}

@Composable
private fun MeasureMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SquareIconButton(
            PhosphorIcons.DotsThreeVertical,
            "Ещё",
            { expanded = true },
            Modifier.testTag("measure-menu"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Название и единица") },
                onClick = {
                    expanded = false
                    onEdit()
                },
                modifier = Modifier.testTag("edit-measure"),
            )
            DropdownMenuItem(
                text = { Text("Удалить показатель") },
                onClick = {
                    expanded = false
                    onDelete()
                },
                modifier = Modifier.testTag("delete-measure"),
            )
        }
    }
}

@Composable
private fun EditMeasureDialog(
    editing: EditMeasureUi,
    onName: (String) -> Unit,
    onUnit: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Показатель") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editing.name,
                    onValueChange = onName,
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.testTag("edit-measure-name"),
                )
                OutlinedTextField(
                    value = editing.unit,
                    onValueChange = onUnit,
                    label = { Text("Единица, например см") },
                    singleLine = true,
                    modifier = Modifier.testTag("edit-measure-unit"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = editing.canSave,
                modifier = Modifier.testTag("confirm-edit-measure"),
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-edit-measure")) {
                Text("Отмена")
            }
        },
    )
}
