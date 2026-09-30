package monster.greyde.kachalochka.ui.visit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.ui.account.AccountsViewModel
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.DragHandle
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.ReorderState
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.components.SquareToggleButton
import monster.greyde.kachalochka.ui.components.dragOutline
import monster.greyde.kachalochka.ui.components.rememberReorderState
import monster.greyde.kachalochka.ui.components.reorderItem
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.MachineThumbnail
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VisitScreen(
    day: CalendarDay,
    pickedMachineId: MachineId?,
    onPickedMachineConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickMachine: (selected: MachineId?) -> Unit,
    onOpenMachineSettings: (MachineId) -> Unit,
) {
    val viewModel: VisitViewModel = koinViewModel { parametersOf(day) }
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
        isBackEnabled = current?.page != null,
        onBackCompleted = { viewModel.closeSheet() },
    )
    val page = current?.page
    if (page != null) {
        MachinePage(
            page = page,
            viewModel = viewModel,
            onOpenSettings = onOpenSettings,
            onOpenMachineSettings = {
                viewModel.openMachineSettings(page.machineId, onOpenMachineSettings)
            },
            onAddAccount = accountsViewModel::addAccount,
        )
        return
    }
    Screen(
        "",
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        actions = {
            if (current?.canOrder == true) {
                SquareToggleButton(
                    PhosphorIcons.Equals,
                    strings().reorder,
                    current.ordering,
                    { viewModel.toggleOrdering() },
                    Modifier.testTag("reorder-toggle"),
                )
            }
            if (current?.canShare == true) {
                SquareIconButton(
                    PhosphorIcons.ShareNetwork,
                    strings().share,
                    viewModel::share,
                    Modifier.testTag("share-visit"),
                )
            }
        },
    ) {
        if (current == null) return@Screen
        VisitList(
            state = current,
            onDismissNotice = viewModel::dismissNotice,
            onOpen = viewModel::openMachine,
            onOpenSettings = { viewModel.openMachineSettings(it, onOpenMachineSettings) },
            onToggleOrdering = viewModel::toggleOrdering,
            onToggleGroupByTag = viewModel::toggleGroupByTag,
            onMoveMachine = viewModel::moveMachine,
            onMoveSet = viewModel::moveSet,
            onNewMachine = { onPickMachine(viewModel.selectedMachineId) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VisitList(
    state: VisitUiState,
    onDismissNotice: () -> Unit,
    onOpen: (MachineId) -> Unit,
    onOpenSettings: (MachineId) -> Unit,
    onToggleOrdering: () -> Unit,
    onToggleGroupByTag: () -> Unit,
    onMoveMachine: (MachineId, Int) -> Unit,
    onMoveSet: (WorkoutSetId, Int) -> Unit,
    onNewMachine: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            state.title,
            modifier = Modifier.padding(bottom = 6.dp).testTag("visit-title"),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onBackground,
        )
        if (state.canGroupByTag) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = state.groupByTag, onValueChange = { onToggleGroupByTag() })
                    .testTag("group-by-tag"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.groupByTag, onCheckedChange = null)
                Text(
                    strings().groupByTag,
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 14.sp,
                    color = colors.onBackground.copy(alpha = 0.7f),
                )
            }
        }
        state.notice?.let {
            Text(
                it,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismissNotice)
                        .padding(vertical = 8.dp)
                        .testTag("visit-notice"),
                fontSize = 15.sp,
                color = colors.onBackground.copy(alpha = 0.6f),
            )
        }
        val machineOrder = rememberReorderState()
        SideEffect { machineOrder.retain(state.groups.size) }
        state.sections.forEach { section ->
            section.title?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp).testTag("section-$it"),
                    fontSize = 13.sp,
                    letterSpacing = 0.09.em,
                    color = colors.secondary,
                )
            }
            section.groups.forEach { group ->
                MachineBlock(
                    group = group,
                    index = state.groups.indexOf(group),
                    order = machineOrder,
                    ordering = state.ordering,
                    onOpen = onOpen,
                    onOpenSettings = onOpenSettings,
                    onDropMachine = {
                        from,
                        to,
                        ->
                        onMoveMachine(state.groups[from].machineId, to)
                    },
                    onMoveSet = onMoveSet,
                )
            }
        }
        if (state.ordering) {
            OutlineButton(
                strings().done,
                PhosphorIcons.Check,
                onToggleOrdering,
                Modifier.fillMaxWidth().padding(top = 14.dp).testTag("finish-ordering"),
            )
        } else {
            OutlineButton(
                strings().add,
                PhosphorIcons.Plus,
                onNewMachine,
                Modifier.fillMaxWidth().padding(top = 14.dp).testTag("pick-machine"),
            )
        }
    }
}

@Composable
private fun MachineBlock(
    group: SetGroupUi,
    index: Int,
    order: ReorderState,
    ordering: Boolean,
    onOpen: (MachineId) -> Unit,
    onOpenSettings: (MachineId) -> Unit,
    onDropMachine: (from: Int, to: Int) -> Unit,
    onMoveSet: (WorkoutSetId, Int) -> Unit,
) {
    val setOrder = rememberReorderState()
    SideEffect { setOrder.retain(group.sets.size) }
    // A dragged set floats over the machine blocks below its own.
    Column(
        Modifier
            .reorderItem(order, index)
            .zIndex(if (setOrder.dragging != null) 1f else 0f)
            .dragOutline(order.dragging == index),
    ) {
        val id = group.machineId.value
        Row(
            Modifier
                .fillMaxWidth()
                .clickableUnless(ordering) { onOpen(group.machineId) }
                .padding(vertical = 12.dp)
                .padding(end = if (ordering) 12.dp else 0.dp)
                .testTag("group-$id"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ordering) DragHandle(order, index, "drag-machine-$id", onDropMachine)
            MachineThumbnail(
                group.photo,
                PhosphorIcons.Barbell,
                Modifier.padding(end = 12.dp).testTag("thumb-$id"),
                size = 44.dp,
                onClick = { onOpenSettings(group.machineId) }.takeUnless { ordering },
            )
            MachineLines(
                title = group.title,
                tags = group.tags,
                note = group.setupNote,
                summary = group.summary,
                tag = id,
                modifier = Modifier.weight(1f),
            )
        }
        if (ordering) {
            group.sets.forEachIndexed { setIndex, row ->
                SetRow(
                    row,
                    ordering = true,
                    onEdit = {},
                    Modifier.reorderItem(setOrder, setIndex),
                    handle = {
                        DragHandle(setOrder, setIndex, "drag-set-${row.id.value}") { from, to ->
                            onMoveSet(group.sets[from].id, to)
                        }
                    },
                    dragged = setOrder.dragging == setIndex,
                )
            }
        }
        Rule()
    }
}

/**
 * A machine's name with its tags, then its comment and its results, each in grey on a line of
 * its own; the results wrap between the weights and the reps when they do not fit one line.
 */
@Composable
internal fun MachineLines(
    title: String,
    tags: List<String>,
    note: String,
    summary: List<String>,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.6f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontSize = 16.sp,
                color = colors.onBackground,
                modifier = Modifier.testTag("group-title-$tag"),
            )
            tags.forEach { TagChip(it, Modifier.testTag("group-tag-$tag-$it")) }
        }
        if (note.isNotBlank()) {
            Text(
                note,
                fontSize = 14.sp,
                color = muted,
                modifier = Modifier.testTag("group-note-$tag"),
            )
        }
        if (summary.isNotEmpty()) {
            FlowRow(
                Modifier.semantics(mergeDescendants = true) {}.testTag("group-summary-$tag"),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                summary.forEach {
                    Text(
                        it,
                        fontSize = 14.sp,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        tag,
        modifier =
            modifier
                .clip(RoundedCornerShape(11.dp))
                .border(1.dp, colors.primary.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 12.sp,
        color = colors.onPrimaryContainer,
    )
}

/** Keeps the drag handle its own node for tests and accessibility. */
internal fun Modifier.clickableUnless(
    ordering: Boolean,
    onClick: () -> Unit,
): Modifier = if (ordering) this else clickable(onClick = onClick)

@Composable
internal fun SetRow(
    row: SetRowUi,
    ordering: Boolean,
    onEdit: (WorkoutSetId) -> Unit,
    modifier: Modifier,
    handle: @Composable () -> Unit = {},
    dragged: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(ControlShape)
            .dragOutline(dragged)
            .then(
                if (row.selected) {
                    Modifier.border(1.dp, colors.primary, ControlShape)
                } else {
                    Modifier
                },
            ).clickableUnless(ordering) { onEdit(row.id) }
            .padding(horizontal = 12.dp, vertical = if (ordering) 0.dp else 10.dp)
            .testTag("set-row-${row.id.value}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ordering) handle()
        Text(row.title, fontSize = 15.sp, color = colors.onBackground.copy(alpha = 0.5f))
        Text(row.value, fontSize = 16.sp, color = colors.onBackground)
        if (row.comment.isNotEmpty()) {
            Text(
                row.comment,
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("set-comment-${row.id.value}"),
            )
        }
    }
}
