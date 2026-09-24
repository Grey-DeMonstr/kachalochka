package monster.greyde.kachalochka.ui.visit

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
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
import monster.greyde.kachalochka.ui.account.MonogramBadge
import monster.greyde.kachalochka.ui.account.dashedCircle
import monster.greyde.kachalochka.ui.components.AccentButton
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
import kotlin.math.roundToInt

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
        isBackEnabled = current?.sheet?.expanded == true,
        onBackCompleted = { viewModel.collapseSheet() },
    )
    Screen(
        current?.title ?: "Визит",
        onBack = { if (!viewModel.collapseSheet()) onBack() },
        onOpenSettings = onOpenSettings,
    ) {
        if (current == null) return@Screen
        VisitList(
            state = current,
            onToggle = viewModel::toggleGroup,
            onEdit = viewModel::editSet,
            onEnd = { viewModel.endVisit(onVisitEnded) },
            onNewMachine = { onPickMachine(viewModel.selectedMachineId) },
            modifier = Modifier.weight(1f),
        )
        current.sheet?.let { sheet ->
            SetSheet(
                sheet = sheet,
                onWeight = viewModel::changeWeight,
                onReps = viewModel::changeReps,
                onSave = viewModel::save,
                onOpenMachineSettings = { viewModel.openMachineSettings(onOpenMachineSettings) },
                onDelete = viewModel::deleteEditedSet,
                onSwitchTo = viewModel::switchTo,
                onAddAccount = accountsViewModel::addAccount,
                onExpand = viewModel::expandSheet,
                onCollapse = { viewModel.collapseSheet() },
            )
        }
    }
}

@Composable
private fun VisitList(
    state: VisitUiState,
    onToggle: (MachineId) -> Unit,
    onEdit: (WorkoutSetId) -> Unit,
    onEnd: () -> Unit,
    onNewMachine: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .alpha(if (state.sheet?.expanded == true) 0.55f else 1f)
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
            if (!state.ended) {
                Text(
                    "Завершить визит",
                    modifier =
                        Modifier.clickable(onClick = onEnd).padding(8.dp).testTag("end-visit"),
                    fontSize = 14.sp,
                    color = colors.tertiary,
                )
            }
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
        OutlineButton(
            "Новый тренажёр",
            PhosphorIcons.Plus,
            onNewMachine,
            Modifier.fillMaxWidth().padding(top = 14.dp).testTag("pick-machine"),
        )
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

private val ChipHeight = 52.dp

/** Frame 5g: one tap moves the sheet between the people signed in, the last chip adds one. */
@Composable
private fun PersonChips(
    people: List<AccountUi>,
    onSwitchTo: (UserId) -> Unit,
    onAddAccount: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        people.forEach { person -> PersonChip(person) { onSwitchTo(person.id) } }
        Box(
            Modifier
                .size(ChipHeight)
                .clip(CircleShape)
                .clickable(onClick = onAddAccount)
                .dashedCircle(colors.onBackground.copy(alpha = 0.35f))
                .testTag("person-add"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.Plus,
                "Добавить аккаунт",
                tint = colors.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PersonChip(
    person: AccountUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
    Row(
        Modifier
            .height(ChipHeight)
            .clip(shape)
            .then(
                if (person.active) {
                    Modifier.background(colors.primary.copy(alpha = 0.14f))
                } else {
                    Modifier
                },
            ).border(
                1.dp,
                if (person.active) colors.primary else colors.onBackground.copy(alpha = 0.16f),
                shape,
            ).selectable(selected = person.active, onClick = onClick)
            .padding(start = 6.dp, end = 16.dp)
            .testTag("person-${person.id.value}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonogramBadge(person.monogram, size = 40.dp, accent = person.active)
        Text(
            person.displayName,
            fontSize = 15.sp,
            color = if (person.active) colors.onPrimaryContainer else colors.onBackground,
        )
    }
}

private val CollapseDistance = 72.dp
private val ExpandDistance = 24.dp
private val FlingSpeed = 800.dp // per second
private val SheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

@Composable
private fun Modifier.sheetChrome(): Modifier {
    val colors = MaterialTheme.colorScheme
    return clip(SheetShape)
        .background(colors.surface)
        .border(1.dp, colors.onBackground.copy(alpha = 0.16f), SheetShape)
        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp)
}

@Composable
private fun Modifier.swipeDownTo(onCollapse: () -> Unit): Modifier {
    val density = LocalDensity.current
    val distance = with(density) { CollapseDistance.toPx() }
    val fling = with(density) { FlingSpeed.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    return draggable(
        state = rememberDraggableState { pulled = (pulled + it).coerceAtLeast(0f) },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            if (pulled > distance || velocity > fling) {
                pulled = 0f
                onCollapse()
            } else {
                animate(pulled, 0f) { value, _ -> pulled = value }
            }
        },
    ).offset { IntOffset(0, pulled.roundToInt()) }
}

@Composable
private fun Modifier.swipeUpTo(onExpand: () -> Unit): Modifier {
    val density = LocalDensity.current
    val distance = with(density) { ExpandDistance.toPx() }
    val fling = with(density) { FlingSpeed.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    return draggable(
        state = rememberDraggableState { pulled += it },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            if (pulled < -distance || velocity < -fling) onExpand()
            pulled = 0f
        },
    )
}

@Composable
private fun SheetHandle(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(width = 44.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.onBackground.copy(alpha = 0.26f)),
    )
}

@Composable
private fun SheetPeek(
    sheet: SheetUi,
    onExpand: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .swipeUpTo(onExpand)
            .clip(SheetShape)
            .clickable(onClick = onExpand)
            .sheetChrome()
            .testTag("sheet-peek"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${sheet.name} · ${sheet.setNumberLabel}",
                modifier = Modifier.weight(1f).testTag("sheet-peek-label"),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.onBackground,
            )
            Icon(
                PhosphorIcons.CaretRight,
                "Развернуть",
                modifier = Modifier.size(20.dp).rotate(-90f),
                tint = colors.onBackground.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun SetSheet(
    sheet: SheetUi,
    onWeight: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onSave: () -> Unit,
    onOpenMachineSettings: () -> Unit,
    onDelete: () -> Unit,
    onSwitchTo: (UserId) -> Unit,
    onAddAccount: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.55f)
    if (!sheet.expanded) {
        SheetPeek(sheet, onExpand)
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .swipeDownTo(onCollapse)
            .sheetChrome()
            .testTag("set-sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        if (sheet.people.size > 1 && !sheet.editing) {
            PersonChips(sheet.people, onSwitchTo, onAddAccount)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumbnail(PhosphorIcons.Barbell)
            Column(
                Modifier
                    .weight(1f)
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
                    onOpenMachineSettings,
                    Modifier.weight(1f).testTag("machine-settings"),
                )
            }
        }
    }
}
