package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.ChoiceRow
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.DISABLED_ALPHA
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MachineFormScreen(
    args: MachineFormArgs,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaved: (MachineId) -> Unit,
    inVisit: Boolean = true,
    onLink: () -> Unit = {},
) {
    val viewModel: MachineFormViewModel = koinViewModel { parametersOf(args) }
    val state by viewModel.state.collectAsState()
    val linking by viewModel.linking.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Screen(
        "Тренажёр",
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        actions = { if (linking.canUnlink) MachineMenu(viewModel::askToUnlink) },
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoAndName(state.name, onChange = { name ->
                viewModel.update { it.copy(name = name) }
            })
            if (linking.linkedWith.isNotEmpty()) {
                Text(
                    "Связан с: ${linking.linkedWith.joinToString(", ")}",
                    modifier = Modifier.testTag("machine-linked-with"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f),
                )
            }
            FieldLabel("Заметка о настройке")
            FormField(
                value = state.setupNote,
                onValueChange = { note -> viewModel.update { it.copy(setupNote = note) } },
                tag = "machine-note",
                minHeight = 62.dp,
                fontSize = 15.sp,
                singleLine = false,
            )
            FieldLabel("Как считается вес")
            WeightModeRow(state, onSelect = { mode ->
                viewModel.update { it.copy(weightMode = mode) }
            })
            PlatformWeightRow(state, onChange = { w ->
                viewModel.update { it.copy(platformWeight = w) }
            })
            PlatformIncludedCard(
                state.platformIncluded,
                onChange = { v -> viewModel.update { it.copy(platformIncluded = v) } },
            )
            UnitRow(state.unit, onSelect = { unit -> viewModel.update { it.copy(unit = unit) } })
            if (state.unit == WeightUnit.Custom) {
                FieldLabel("Название единицы")
                FormField(
                    value = state.unitLabel,
                    onValueChange = { label ->
                        viewModel.update {
                            it.copy(unitLabel = label.take(MachineFormState.UNIT_LABEL_LENGTH))
                        }
                    },
                    tag = "unit-label",
                    minHeight = 50.dp,
                    fontSize = 17.sp,
                    singleLine = true,
                )
            }
            WeightStepRow(state, onChange = { step ->
                viewModel.update { it.copy(weightStep = step) }
            })
            PerLimbCard()
            if (linking.canLink) {
                OutlineButton(
                    "Привязать к…",
                    PhosphorIcons.LinkSimple,
                    onLink,
                    Modifier.fillMaxWidth().testTag("link-machine"),
                )
            }
            linking.error?.let {
                Text(
                    it,
                    modifier = Modifier.testTag("machine-link-error"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (inVisit) HintRow()
        }
        Column(
            Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Rule()
            AccentButton(
                "Сохранить тренажёр",
                PhosphorIcons.Check,
                { viewModel.save(onSaved) },
                Modifier.testTag("save-machine"),
                enabled = state.canSave,
            )
        }
    }
    if (linking.confirmingUnlink) {
        ConfirmDialog(
            title = "Отвязать тренажёр?",
            text = "Результаты друзей на этом тренажёре перестанут показываться у вас.",
            confirmLabel = "Отвязать",
            confirmTag = "confirm-unlink",
            cancelTag = "cancel-unlink",
            onConfirm = viewModel::confirmUnlink,
            onCancel = viewModel::cancelUnlink,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
    )
}

@Composable
private fun PhotoAndName(
    name: String,
    onChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier
                .size(100.dp)
                .alpha(DISABLED_ALPHA)
                .clip(shape)
                .border(1.dp, colors.primary, shape)
                .background(colors.primary.copy(alpha = 0.10f))
                .clickable(enabled = false) {}
                .testTag("machine-photo"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                PhosphorIcons.Camera,
                null,
                tint = colors.tertiary,
                modifier = Modifier.size(30.dp),
            )
            Text("Снять фото", fontSize = 13.sp, color = colors.tertiary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Название")
            FormField(
                value = name,
                onValueChange = onChange,
                tag = "machine-name",
                minHeight = 50.dp,
                fontSize = 18.sp,
                singleLine = true,
            )
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    minHeight: Dp,
    fontSize: TextUnit,
    singleLine: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(fontSize = fontSize, color = colors.onBackground),
        cursorBrush = SolidColor(colors.secondary),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.onBackground.copy(alpha = 0.16f), shape)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag(tag),
    )
}

@Composable
private fun WeightModeRow(
    state: MachineFormState,
    onSelect: (WeightMode) -> Unit,
) {
    val modes = WeightMode.entries
    ChoiceRow(
        choices =
            listOf(
                Choice("Всего", "mode-total"),
                Choice("На сторону", "mode-per-side"),
            ),
        selected = modes.indexOf(state.weightMode),
        onSelect = { onSelect(modes[it]) },
    )
}

@Composable
private fun PlatformWeightRow(
    state: MachineFormState,
    onChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Вес платформы", fontSize = 15.sp, color = colors.onBackground)
            Text(
                "Своя масса тренажёра",
                fontSize = 12.sp,
                color = colors.onBackground.copy(alpha = 0.48f),
            )
        }
        NumberField(
            state.platformWeight,
            onChange,
            unitLabel(state.unit, state.unitLabel),
            "platform-weight",
            "platform-weight-unit",
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    unit: String,
    tag: String,
    unitTag: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = 50.dp)
            .widthIn(min = 104.dp)
            .clip(ControlShape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.onBackground.copy(alpha = 0.16f), ControlShape)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onBackground,
                ),
            cursorBrush = SolidColor(colors.secondary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.testTag(tag),
        )
        Text(
            unit,
            fontSize = 13.sp,
            color = colors.onBackground.copy(alpha = 0.55f),
            modifier = if (unitTag != null) Modifier.testTag(unitTag) else Modifier,
        )
    }
}

@Composable
private fun PlatformIncludedCard(
    included: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(colors.surface)
            .border(1.dp, colors.onBackground.copy(alpha = 0.12f), ControlShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = included,
                onCheckedChange = onChange,
                modifier = Modifier.testTag("platform-included"),
            )
            Text("Прибавлять к записи", fontSize = 15.sp, color = colors.onBackground)
        }
        Text(
            if (included) {
                "Включено: вес платформы входит в каждую запись."
            } else {
                "Выключено: записывается только навесной вес, а платформа стоит рядом с " +
                    "названием — «Жим ногами (+25 кг) 70 кг × 10»."
            },
            fontSize = 12.sp,
            color = colors.onBackground.copy(alpha = 0.50f),
        )
    }
}

@Composable
private fun UnitRow(
    unit: WeightUnit,
    onSelect: (WeightUnit) -> Unit,
) {
    val units = WeightUnit.entries
    ChoiceRow(
        choices =
            listOf(
                Choice("кг", "unit-kg"),
                Choice("lb", "unit-lb"),
                Choice("Своя единица", "unit-custom", weight = 2f),
            ),
        selected = units.indexOf(unit),
        onSelect = { onSelect(units[it]) },
    )
}

@Composable
private fun WeightStepRow(
    state: MachineFormState,
    onChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Шаг веса",
            modifier = Modifier.width(82.dp),
            fontSize = 14.sp,
            color = colors.onBackground.copy(alpha = 0.72f),
        )
        Spacer(Modifier.weight(1f))
        NumberField(
            state.weightStep,
            onChange,
            unitLabel(state.unit, state.unitLabel),
            "weight-step",
        )
    }
}

@Composable
private fun PerLimbCard() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(DISABLED_ALPHA)
            .clip(ControlShape)
            .background(colors.surface)
            .border(1.dp, colors.onBackground.copy(alpha = 0.12f), ControlShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Считать левую и правую отдельно",
                    fontSize = 16.sp,
                    color = colors.onBackground,
                )
                Text(
                    "Для односторонних тренажёров — в подходе два числа",
                    fontSize = 12.sp,
                    color = colors.onBackground.copy(alpha = 0.50f),
                )
            }
            Switch(
                checked = false,
                onCheckedChange = {},
                enabled = false,
                modifier = Modifier.testTag("per-limb"),
            )
        }
    }
}

@Composable
private fun HintRow() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.testTag("machine-visit-hint"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            PhosphorIcons.Info,
            null,
            tint = colors.onBackground.copy(alpha = 0.50f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            "После сохранения тренажёр появится в этом визите.",
            fontSize = 13.sp,
            color = colors.onBackground.copy(alpha = 0.50f),
        )
    }
}

@Composable
private fun MachineMenu(onUnlink: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SquareIconButton(
            PhosphorIcons.DotsThreeVertical,
            "Ещё",
            { expanded = true },
            Modifier.testTag("machine-menu"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Отвязать от друзей") },
                onClick = {
                    expanded = false
                    onUnlink()
                },
                modifier = Modifier.testTag("unlink-machine"),
            )
        }
    }
}
