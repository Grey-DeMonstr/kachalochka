package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.ui.calendar.MonthGrid
import monster.greyde.kachalochka.ui.calendar.MonthHeader
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.AllResults
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.components.Stepper
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MeasureScreen(
    measureId: MeasureId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onGone: () -> Unit,
) {
    val viewModel: MeasureViewModel = koinViewModel { parametersOf(measureId) }
    val state by viewModel.state.collectAsState()
    val gone by viewModel.gone.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(gone) { if (gone) onGone() }
    val measure = state
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = measure?.history != null,
        onBackCompleted = { viewModel.closeHistory() },
    )
    Screen(
        measure?.title.orEmpty(),
        onBack = { if (!viewModel.closeHistory()) onBack() },
        onOpenSettings = onOpenSettings,
        actions = {
            if (measure != null && (measure.canEdit || measure.canDelete)) {
                MeasureMenu(
                    onEdit = viewModel::openEdit.takeIf { measure.canEdit },
                    onDelete = viewModel::askDelete.takeIf { measure.canDelete },
                )
            }
        },
    ) {
        if (measure == null) return@Screen
        val scroll = rememberScrollState()
        // A result low in the list opens the history, whose calendar is at the top.
        LaunchedEffect(measure.history != null) { scroll.scrollTo(0) }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val history = measure.history
            if (history == null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel(measure.entry.label, Modifier.testTag("measure-entry-title"))
                    Hint(measure)
                }
                Entry(measure, viewModel)
                MeasureSummary(measure.latest, measure.change, measure.lastMeasured)
                PeriodChoice(measure.period, viewModel::choose)
                PeriodChart(
                    measure.points,
                    { measureValue(it, measure.unit) },
                    measure.phases,
                    measure.phasesOnly,
                )
                OutlineButton(
                    strings().editHistory,
                    PhosphorIcons.CalendarBlank,
                    viewModel::openHistory,
                    Modifier.fillMaxWidth().testTag("edit-history"),
                )
                if (measure.results.isNotEmpty()) {
                    Column { AllResults(measure.results, "measure-history", viewModel::openDay) }
                }
            } else {
                MonthHeader(
                    history.monthTitle,
                    history.canShowNextMonth,
                    onPrevious = { viewModel.showHistoryMonth(-1) },
                    onNext = { viewModel.showHistoryMonth(+1) },
                )
                MonthGrid(history.weeks, onSelect = viewModel::chooseDay)
                Rule()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        measure.entry.label,
                        Modifier.testTag("measure-entry-title"),
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Hint(measure)
                }
                Entry(measure, viewModel)
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
            title = strings().deleteMeasureTitle,
            text = strings().deleteMeasureText(measure.name),
            confirmLabel = strings().delete,
            confirmTag = "confirm-delete-measure",
            cancelTag = "cancel-delete-measure",
            onConfirm = viewModel::confirmDelete,
            onCancel = viewModel::cancelDelete,
        )
    }
}

/** As the form shows it under the measure's name. */
@Composable
private fun Hint(measure: MeasureUi) {
    measure.hint?.let {
        Text(
            it,
            Modifier.testTag("measure-hint"),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
    }
}

/** The entry day's value; the history can also delete it. */
@Composable
private fun Entry(
    measure: MeasureUi,
    viewModel: MeasureViewModel,
) {
    val entry = measure.entry
    Stepper(
        value = entry.text,
        caption = null,
        onMinus = { viewModel.stepValue(-1) },
        onPlus = { viewModel.stepValue(+1) },
        tag = "entry",
        onValueChange = viewModel::typeValue,
        suffix = measure.unit,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (measure.history != null && entry.canDelete) {
            SquareIconButton(
                PhosphorIcons.Trash,
                strings().delete,
                viewModel::deleteValue,
                Modifier.testTag("delete-value"),
            )
        }
        AccentButton(
            strings().save,
            PhosphorIcons.Check,
            viewModel::saveValue,
            Modifier.weight(1f).testTag("save-value"),
            height = 56.dp,
            enabled = entry.canSave,
        )
    }
}

@Composable
private fun MeasureMenu(
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SquareIconButton(
            PhosphorIcons.DotsThreeVertical,
            strings().more,
            { expanded = true },
            Modifier.testTag("measure-menu"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onEdit?.let {
                DropdownMenuItem(
                    text = { Text(strings().nameAndUnit) },
                    onClick = {
                        expanded = false
                        it()
                    },
                    modifier = Modifier.testTag("edit-measure"),
                )
            }
            onDelete?.let {
                DropdownMenuItem(
                    text = { Text(strings().deleteMeasure) },
                    onClick = {
                        expanded = false
                        it()
                    },
                    modifier = Modifier.testTag("delete-measure"),
                )
            }
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
        title = { Text(strings().measure) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editing.name,
                    onValueChange = onName,
                    label = { Text(strings().name) },
                    singleLine = true,
                    modifier = Modifier.testTag("edit-measure-name"),
                )
                OutlinedTextField(
                    value = editing.unit,
                    onValueChange = onUnit,
                    label = { Text(strings().unitExample) },
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
                Text(strings().save)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-edit-measure")) {
                Text(strings().cancel)
            }
        },
    )
}
