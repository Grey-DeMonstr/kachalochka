package monster.greyde.kachalochka.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.AccountUi
import monster.greyde.kachalochka.ui.account.AccountsViewModel
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.ChoiceRow
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.RestTimerChip
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.Stepper
import monster.greyde.kachalochka.ui.components.Thumbnail
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VisitScreen(
    visitId: VisitId,
    pickedMachineId: MachineId?,
    onPickedMachineConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickMachine: (selected: MachineId?) -> Unit,
    onOpenMachineSettings: (MachineId) -> Unit,
    onVisitEnded: () -> Unit,
) {
    val viewModel: VisitViewModel = koinViewModel { parametersOf(visitId) }
    val accountsViewModel: AccountsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(pickedMachineId) {
        if (pickedMachineId != null) {
            viewModel.selectMachine(pickedMachineId)
            onPickedMachineConsumed()
        }
    }
    val current = state
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = current?.sheet?.editing == true,
        onBackCompleted = { viewModel.leaveEdit() },
    )
    Screen(
        "Визит",
        onBack = { if (!viewModel.leaveEdit()) onBack() },
        onOpenSettings = onOpenSettings,
    ) {
        if (current == null) return@Screen
        VisitList(
            state = current,
            onToggle = viewModel::toggleGroup,
            onEdit = viewModel::editSet,
            onEnd = { viewModel.endVisit(onVisitEnded) },
            modifier = Modifier.weight(1f),
        )
        SetSheet(
            sheet = current.sheet,
            onPickMachine = { onPickMachine(viewModel.selectedMachineId) },
            onWeight = viewModel::changeWeight,
            onReps = viewModel::changeReps,
            onSave = viewModel::save,
            onOpenMachineSettings = onOpenMachineSettings,
            onDelete = viewModel::deleteEditedSet,
            onSwitchTo = viewModel::switchTo,
            onAddAccount = accountsViewModel::addAccount,
        )
    }
}

@Composable
private fun VisitList(
    state: VisitUiState,
    onToggle: (MachineId) -> Unit,
    onEdit: (WorkoutSetId) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .alpha(if (state.sheet != null) 0.55f else 1f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.setCountLabel.uppercase(),
                modifier = Modifier.testTag("visit-set-count"),
                fontSize = 13.sp,
                letterSpacing = 0.09.em,
                color = colors.onBackground.copy(alpha = 0.5f),
            )
            Text(
                "Завершить визит",
                modifier = Modifier.clickable(onClick = onEnd).padding(8.dp).testTag("end-visit"),
                fontSize = 14.sp,
                color = colors.tertiary,
            )
        }
        state.groups.forEach { group ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(group.machineId) }
                    .padding(vertical = 12.dp)
                    .testTag("group-${group.machineId.value}"),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    group.title,
                    fontSize = 16.sp,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    group.summary,
                    fontSize = 15.sp,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            }
            if (group.expanded) group.sets.forEach { SetRow(it, onEdit) }
            Rule()
        }
    }
}

@Composable
private fun SetRow(
    row: SetRowUi,
    onEdit: (WorkoutSetId) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(ControlShape)
            .then(
                if (row.selected) {
                    Modifier
                        .border(1.dp, colors.primary, ControlShape)
                        .background(colors.primary.copy(alpha = 0.14f))
                } else {
                    Modifier
                },
            ).clickable { onEdit(row.id) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("set-row-${row.id.value}"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            row.title,
            fontSize = 16.sp,
            color = if (row.selected) colors.onPrimaryContainer else colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.value,
            fontSize = 15.sp,
            color = if (row.selected) colors.tertiary else colors.onBackground.copy(alpha = 0.6f),
        )
    }
}

/** Frame 5g: one tap moves the sheet between the people signed in, the last chip adds one. */
@Composable
private fun PersonChips(
    people: List<AccountUi>,
    onSwitchTo: (UserId) -> Unit,
    onAddAccount: () -> Unit,
) {
    ChoiceRow(
        choices =
            people.map { Choice(it.displayName, "person-${it.id.value}") } +
                Choice("+", "person-add", weight = 0.4f),
        selected = people.indexOfFirst { it.active },
        onSelect = { index ->
            val person = people.getOrNull(index)
            if (person == null) onAddAccount() else onSwitchTo(person.id)
        },
    )
}

@Composable
private fun SetSheet(
    sheet: SheetUi?,
    onPickMachine: () -> Unit,
    onWeight: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onSave: () -> Unit,
    onOpenMachineSettings: (MachineId) -> Unit,
    onDelete: () -> Unit,
    onSwitchTo: (UserId) -> Unit,
    onAddAccount: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val muted = colors.onBackground.copy(alpha = 0.55f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.onBackground.copy(alpha = 0.16f), shape)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp)
            .testTag("set-sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 44.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.onBackground.copy(alpha = 0.26f)),
        )
        if (sheet == null) {
            AccentButton(
                "Выбрать тренажёр",
                PhosphorIcons.MagnifyingGlass,
                onPickMachine,
                Modifier.testTag("pick-machine"),
            )
            return@Column
        }
        if (sheet.people.size > 1 && !sheet.editing) {
            PersonChips(sheet.people, onSwitchTo, onAddAccount)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumbnail(PhosphorIcons.Barbell)
            Column(
                Modifier
                    .weight(1f)
                    .clickable(enabled = !sheet.editing, onClick = onPickMachine)
                    .testTag("sheet-machine"),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        buildAnnotatedString {
                            append(sheet.name)
                            sheet.platformSuffix?.let {
                                withStyle(
                                    SpanStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = muted,
                                    ),
                                ) {
                                    append(" $it")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("sheet-machine-name"),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.onBackground,
                    )
                    Text(
                        sheet.setNumberLabel,
                        modifier = Modifier.testTag("sheet-set-number"),
                        fontSize = 13.sp,
                        color = if (sheet.editing) colors.secondary else muted,
                    )
                }
                sheet.caption?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("sheet-caption"),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colors.onBackground.copy(alpha = 0.58f),
                    )
                }
            }
        }
        if (!sheet.editing) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sheet.previous.orEmpty(),
                    modifier = Modifier.weight(1f).testTag("sheet-previous"),
                    fontSize = 13.sp,
                    color = colors.secondary,
                )
                RestTimerChip(Modifier.testTag("sheet-rest-timer"), pill = true)
            }
        }
        Stepper(
            sheet.weight,
            sheet.weightCaption,
            { onWeight(-1) },
            { onWeight(+1) },
            "weight",
            accent = sheet.editing,
        )
        Stepper(sheet.reps, "повторы", { onReps(-1) }, { onReps(+1) }, "reps")
        AccentButton(
            sheet.saveLabel,
            PhosphorIcons.Check,
            onSave,
            Modifier.testTag("save-set"),
            height = 72.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(
                "Комментарий",
                PhosphorIcons.ChatTeardropText,
                {},
                Modifier.weight(1f).testTag("set-comment"),
                enabled = false,
            )
            if (sheet.editing) {
                OutlineButton(
                    "Удалить подход",
                    PhosphorIcons.Trash,
                    onDelete,
                    Modifier.weight(1f).testTag("delete-set"),
                )
            } else {
                OutlineButton(
                    "Настройки",
                    PhosphorIcons.SlidersHorizontal,
                    { onOpenMachineSettings(sheet.machineId) },
                    Modifier.weight(1f).testTag("machine-settings"),
                )
            }
        }
    }
}
