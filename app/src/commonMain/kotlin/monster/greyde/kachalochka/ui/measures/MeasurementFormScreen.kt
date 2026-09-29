package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** [initialDay] null opens today. */
@Composable
fun MeasurementFormScreen(
    initialDay: CalendarDay?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: MeasurementFormViewModel = koinViewModel { parametersOf(initialDay) }
    val state by viewModel.state.collectAsState()
    Screen(strings().measurement, onBack = onBack, onOpenSettings = onOpenSettings) {
        val form = state ?: return@Screen
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlineButton(
                form.dayTitle,
                PhosphorIcons.CalendarBlank,
                viewModel::pickDay,
                Modifier.fillMaxWidth().testTag("measurement-day"),
            )
            form.fields.forEach { field ->
                MeasureField(field, onType = { viewModel.type(field.id, it) })
            }
            if (form.canDelete) {
                OutlineButton(
                    strings().deleteMeasurement,
                    PhosphorIcons.Trash,
                    viewModel::askDelete,
                    Modifier.fillMaxWidth().testTag("delete-measurement"),
                )
            }
        }
        Column(
            Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Rule()
            AccentButton(
                strings().save,
                PhosphorIcons.Check,
                { viewModel.save(onSaved) },
                Modifier.testTag("save-measurement"),
                enabled = form.canSave,
            )
        }
        form.picker?.let {
            DayPickerDialog(
                it,
                onSelect = viewModel::chooseDay,
                onPrevious = { viewModel.showMonth(-1) },
                onNext = { viewModel.showMonth(+1) },
                onDismiss = viewModel::dismissPick,
            )
        }
        if (form.deleting) {
            ConfirmDialog(
                title = strings().deleteMeasurementTitle,
                text = strings().deleteMeasurementText(form.dayTitle),
                confirmLabel = strings().delete,
                confirmTag = "confirm-delete-measurement",
                cancelTag = "cancel-delete-measurement",
                onConfirm = { viewModel.confirmDelete(onDeleted) },
                onCancel = viewModel::cancelDelete,
            )
        }
    }
}

/** The placeholder is the previous value, so a steady measure needs only a glance. */
@Composable
private fun MeasureField(
    field: MeasureFieldUi,
    onType: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.45f)
    val id = field.id.value
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(field.name, fontSize = 16.sp, color = colors.onBackground)
            field.howTo?.let {
                Text(
                    it,
                    Modifier.padding(top = 2.dp).testTag("measure-how-to-$id"),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.onBackground.copy(alpha = 0.55f),
                )
            }
        }
        Row(
            Modifier
                .width(140.dp)
                .heightIn(min = 50.dp)
                .clip(ControlShape)
                .background(colors.surfaceVariant)
                .border(
                    1.dp,
                    if (field.valid) colors.onBackground.copy(alpha = 0.16f) else colors.error,
                    ControlShape,
                ).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Box(Modifier.weight(1f)) {
                if (field.text.isEmpty()) {
                    field.hint?.let {
                        Text(
                            it,
                            Modifier.testTag("measure-hint-$id"),
                            style = style,
                            color = muted,
                        )
                    }
                }
                BasicTextField(
                    value = field.text,
                    onValueChange = onType,
                    singleLine = true,
                    textStyle = style.copy(color = colors.onBackground),
                    cursorBrush = SolidColor(colors.secondary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("measure-field-$id"),
                )
            }
            Text(field.unit, fontSize = 13.sp, color = colors.onBackground.copy(alpha = 0.55f))
        }
    }
}
