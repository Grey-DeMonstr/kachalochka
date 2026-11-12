package monster.greyde.kachalochka.ui.visit

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.AccountUi
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.DragHandle
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.components.Stepper
import monster.greyde.kachalochka.ui.components.TagChip
import monster.greyde.kachalochka.ui.components.TextInput
import monster.greyde.kachalochka.ui.components.dragOutline
import monster.greyde.kachalochka.ui.components.rememberReorderState
import monster.greyde.kachalochka.ui.components.reorderItem
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.machine.MachineMenu
import monster.greyde.kachalochka.ui.machine.MachinePlacementViewModel
import monster.greyde.kachalochka.ui.machine.PlacedMachine
import monster.greyde.kachalochka.ui.machine.PlacementNotice
import monster.greyde.kachalochka.ui.machine.PlanChooser
import monster.greyde.kachalochka.ui.photos.MachineThumbnail
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/** Frame 7c: one machine of the visit, its record, last time, friends and the day's sets. */
@Composable
internal fun MachinePage(
    page: MachinePageUi,
    viewModel: VisitViewModel,
    onOpenSettings: () -> Unit,
    onOpenMachineSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
    canPlanToday: Boolean,
    onOpenVisit: (CalendarDay) -> Unit,
    onNewPlan: (MachineId) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val placement: MachinePlacementViewModel = koinViewModel()
    val placed = PlacedMachine(page.machineId)
    Screen(
        "",
        onBack = { viewModel.closeSheet() },
        onOpenSettings = onOpenSettings,
        actions = {
            SquareIconButton(
                PhosphorIcons.ChartLineUp,
                strings().statistics,
                onOpenStatistics,
                Modifier.testTag("machine-statistics"),
            )
            if (!page.ordering) {
                MachineMenu(
                    onToPlan = { placement.choosePlan(placed) },
                    onToday =
                        if (canPlanToday && !page.readOnly) {
                            { placement.planToday(placed, onOpen = onOpenVisit) }
                        } else {
                            null
                        },
                    onReorder = (viewModel::toggleSetOrdering).takeIf { page.canOrder },
                )
            }
        },
        timer = true,
    ) {
        PlacementNotice(placement, page.machineId, Modifier.padding(horizontal = 16.dp))
        Box(Modifier.weight(1f).fillMaxWidth().testTag("machine-page")) {
            MachinePager(page, viewModel::openMachine) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .alpha(if (page.form != null) 0.45f else 1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MachineHeader(
                        page.name,
                        page.platformSuffix,
                        page.photo,
                        page.tags,
                        page.setupNote,
                        "page",
                        onOpenSettings = onOpenMachineSettings.takeUnless { page.readOnly },
                    )
                    if (page.record != null || page.previous != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            page.record?.let {
                                StatCard(strings().record, it, "page-record", Modifier.weight(1f))
                            }
                            page.previous?.let {
                                StatCard(
                                    page.previousAgo.orEmpty(),
                                    it,
                                    "page-previous",
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (page.friends.isNotEmpty()) {
                        Column(
                            Modifier.testTag("page-friends"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            page.friends.forEach { FriendLine(it) }
                        }
                    }
                    page.daySets?.let {
                        SectionLabel(it, Modifier.padding(top = 4.dp).testTag("page-day-sets"))
                    }
                    val setOrder = rememberReorderState()
                    SideEffect { setOrder.retain(page.sets.size) }
                    val pyramidName = strings().pyramid
                    val rowLook =
                        Modifier
                            .clip(ControlShape)
                            .background(colors.onBackground.copy(alpha = 0.04f))
                    if (!page.ordering) {
                        SetBlocks(
                            page.blocks,
                            (viewModel::editSet).takeUnless { page.readOnly },
                            viewModel::togglePyramidOpen,
                            rowLook,
                        )
                    } else {
                        Column {
                            page.sets.forEachIndexed { index, row ->
                                Row(
                                    Modifier
                                        .reorderItem(setOrder, index)
                                        .dragOutline(setOrder.dragging == index),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    DragHandle(setOrder, index, "drag-set-${row.id.value}") {
                                        from,
                                        to,
                                        ->
                                        viewModel.moveSet(page.sets[from].id, to)
                                    }
                                    if (row.pyramid) {
                                        PyramidIcon(
                                            Modifier
                                                .padding(end = 8.dp)
                                                .semantics { contentDescription = pyramidName }
                                                .testTag("pyramid-mark-${row.id.value}"),
                                        )
                                    }
                                    SetRow(row, null, Modifier.weight(1f).then(rowLook))
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (page.ordering) {
                            OutlineButton(
                                strings().done,
                                PhosphorIcons.Check,
                                viewModel::toggleSetOrdering,
                                Modifier.weight(1f).testTag("finish-ordering"),
                            )
                        } else if (!page.readOnly) {
                            OutlineButton(
                                strings().add,
                                PhosphorIcons.Plus,
                                viewModel::openForm,
                                Modifier.weight(1f).testTag("add-set"),
                            )
                        }
                        if (page.canUnplan) {
                            OutlineButton(
                                strings().unplan,
                                PhosphorIcons.Trash,
                                { viewModel.unplan(page.machineId) },
                                Modifier.weight(1f).testTag("unplan"),
                            )
                        }
                    }
                }
            }
            page.form?.let {
                SetForm(
                    form = it,
                    viewModel = viewModel,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
    PlanChooser(placement, onNewPlan)
}

/**
 * Swipes through the visit's machines. A neighbour shows its page's header until the view model
 * opens it. The open form and the set handles keep their gestures to themselves.
 */
@Composable
private fun MachinePager(
    page: MachinePageUi,
    onOpen: (MachineId) -> Unit,
    content: @Composable () -> Unit,
) {
    val ids = page.swipeOrder.map { it.machineId }.ifEmpty { listOf(page.machineId) }
    val index = ids.indexOf(page.machineId)
    // Each machine gets a pager at rest on it, so only a swipe can settle on another page.
    key(page.machineId, index) {
        val pager = rememberPagerState(index) { ids.size }
        val shown by rememberUpdatedState(ids)
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect {
                if (it != index) shown.getOrNull(it)?.let(onOpen)
            }
        }
        HorizontalPager(
            pager,
            Modifier.fillMaxSize(),
            userScrollEnabled = page.form == null && !page.ordering,
            key = { ids[it].value },
        ) { shownIndex ->
            if (shownIndex == index) {
                content()
            } else {
                page.swipeOrder.getOrNull(shownIndex)?.let { MachinePreview(it) }
            }
        }
    }
}

/** Drawn as the page's own header, so the swipe does not change it when the page opens. */
@Composable
private fun MachinePreview(group: SetGroupUi) {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        MachineHeader(
            group.name,
            group.platformSuffix,
            group.photo,
            group.tags,
            group.setupNote,
            "preview-${group.machineId.value}",
        )
    }
}

@Composable
private fun MachineHeader(
    name: String,
    platformSuffix: String?,
    photo: Photo?,
    tags: List<String>,
    note: String,
    tag: String,
    onOpenSettings: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        MachineThumbnail(
            photo,
            PhosphorIcons.Barbell,
            Modifier.testTag("$tag-thumb"),
            size = 64.dp,
            onOpenSettings = onOpenSettings,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                buildAnnotatedString {
                    append(name)
                    platformSuffix?.let {
                        withStyle(
                            SpanStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.onBackground.copy(alpha = 0.55f),
                            ),
                        ) { append(" $it") }
                    }
                },
                modifier = Modifier.testTag("$tag-machine-name"),
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onBackground,
            )
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    tags.forEach { TagChip(it, Modifier.testTag("$tag-tag-$it")) }
                }
            }
            if (note.isNotBlank()) {
                Text(
                    note,
                    modifier = Modifier.testTag("$tag-note"),
                    fontSize = 15.sp,
                    color = colors.onBackground.copy(alpha = 0.68f),
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(ControlShape)
            .border(1.dp, colors.onBackground.copy(alpha = 0.12f), ControlShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionLabel(label)
        Text(value, fontSize = 16.sp, color = colors.onBackground)
    }
}

@Composable
private fun FriendLine(friend: FriendLineUi) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.testTag("friend-line-${friend.id.value}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(friend.id, friend.name, friend.avatar, size = 24.dp)
        Text(
            buildAnnotatedString {
                append(friend.name)
                withStyle(SpanStyle(color = colors.onBackground.copy(alpha = 0.55f))) {
                    append("  ${friend.text}")
                }
            },
            fontSize = 14.sp,
            color = colors.onBackground,
        )
    }
}

private val ChipHeight = 48.dp
private val CloseDistance = 72.dp
private val FlingSpeed = 800.dp // per second
private val SheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

/** Frames 7c1 and 7d: the set being added or corrected, over the page. */
@Composable
private fun SetForm(
    form: SetFormUi,
    viewModel: VisitViewModel,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .swipeDownTo { viewModel.cancelForm() }
            .clip(SheetShape)
            .background(colors.surface)
            .border(1.dp, colors.onBackground.copy(alpha = 0.16f), SheetShape)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)
            .testTag("set-sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 44.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.onBackground.copy(alpha = 0.26f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonChips(form.people, viewModel::switchTo, Modifier.weight(1f))
            SetMenu(form, viewModel)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                form.number,
                modifier = Modifier.testTag("set-number"),
                fontSize = 14.sp,
                color = colors.secondary,
            )
            Text(
                form.title,
                modifier = Modifier.testTag("set-title"),
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.7f),
            )
            if (form.pyramid) {
                val pyramidName = strings().pyramid
                PyramidIcon(
                    Modifier
                        .semantics { contentDescription = pyramidName }
                        .testTag("set-pyramid-mark"),
                )
            }
        }
        Stepper(
            form.weight,
            caption = null,
            { viewModel.changeWeight(-1) },
            { viewModel.changeWeight(+1) },
            "weight",
            onValueChange = viewModel::typeWeight,
            accent = form.editing,
            prefix = form.weightSign,
            suffix = form.weightUnit,
            note = form.converted,
        )
        Stepper(
            form.reps,
            strings().reps,
            { viewModel.changeReps(-1) },
            { viewModel.changeReps(+1) },
            "reps",
            onValueChange = viewModel::typeReps,
            keyboardType = KeyboardType.Number,
        )
        if (form.commentShown) {
            TextInput(
                form.comment,
                strings().comment,
                viewModel::typeComment,
                Modifier.testTag("set-comment-field"),
                KeyboardOptions.Default,
                singleLine = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (form.editing) {
                SquareIconButton(
                    PhosphorIcons.Trash,
                    strings().deleteSet,
                    viewModel::deleteEditedSet,
                    Modifier.testTag("delete-set"),
                )
            }
            OutlineButton(
                strings().cancel,
                PhosphorIcons.X,
                viewModel::cancelForm,
                Modifier.weight(1f).testTag("cancel-set"),
            )
            AccentButton(
                form.saveLabel,
                if (form.editing) PhosphorIcons.Check else PhosphorIcons.Plus,
                viewModel::save,
                Modifier.weight(1.4f).testTag("save-set"),
                height = 56.dp,
                enabled = form.canSave,
                loading = form.saving,
            )
        }
    }
}

/** Frame 12a4: the day's sets, a pyramid as one row that opens to its sets. */
@Composable
private fun SetBlocks(
    blocks: List<SetBlockUi>,
    onEdit: ((WorkoutSetId) -> Unit)?,
    onToggle: (WorkoutSetId) -> Unit,
    rowLook: Modifier,
) {
    val line = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Column {
        blocks.forEach { block ->
            val pyramid = block.pyramid
            if (pyramid == null) {
                block.sets.forEach { SetRow(it, onEdit, rowLook) }
            } else {
                PyramidRow(pyramid) { onToggle(pyramid.start) }
                if (pyramid.expanded) {
                    Column(
                        Modifier
                            .padding(start = 22.dp)
                            .drawBehind {
                                drawLine(
                                    line,
                                    Offset.Zero,
                                    Offset(0f, size.height),
                                    strokeWidth = 1.5.dp.toPx(),
                                )
                            }.padding(start = 12.dp),
                    ) {
                        block.sets.forEach { SetRow(it, onEdit, rowLook) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PyramidRow(
    pyramid: PyramidUi,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(ControlShape)
            .background(colors.primary.copy(alpha = 0.09f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("pyramid-${pyramid.start.value}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(pyramid.numbers, fontSize = 15.sp, color = colors.onBackground.copy(alpha = 0.5f))
        PyramidIcon()
        Text(pyramid.best, Modifier.weight(1f), fontSize = 16.sp, color = colors.onBackground)
        Icon(
            if (pyramid.expanded) PhosphorIcons.CaretUp else PhosphorIcons.CaretDown,
            null,
            tint = colors.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Three bars that step down; the same for every pyramid, whatever its weights. */
@Composable
private fun PyramidIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.secondary
    Row(
        modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(20.dp, 16.dp, 11.dp).forEach {
            Box(
                Modifier
                    .size(width = 5.dp, height = it)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun Modifier.swipeDownTo(onClose: () -> Unit): Modifier {
    val density = LocalDensity.current
    val distance = with(density) { CloseDistance.toPx() }
    val fling = with(density) { FlingSpeed.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    return draggable(
        state = rememberDraggableState { pulled = (pulled + it).coerceAtLeast(0f) },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            if (pulled > distance || velocity > fling) {
                pulled = 0f
                onClose()
            } else {
                animate(pulled, 0f) { value, _ -> pulled = value }
            }
        },
    ).offset { IntOffset(0, pulled.roundToInt()) }
}

/** Frame 5g: one tap moves the form between the people shown. */
@Composable
private fun PersonChips(
    people: List<AccountUi>,
    onSwitchTo: (UserId) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .testTag("person-chips"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        people.forEach { person -> PersonChip(person) { onSwitchTo(person.id) } }
    }
}

/** The "⋮" button of the set form, over check boxes for the form's options. */
@Composable
private fun SetMenu(
    form: SetFormUi,
    viewModel: VisitViewModel,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(ControlShape)
                .clickable { open = true }
                .testTag("set-menu"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.DotsThreeVertical,
                strings().more,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CheckMenuItem(form.pyramidLabel, form.pyramid, "set-menu-pyramid", {
                open = false
                viewModel.togglePyramidStart()
            }) { PyramidIcon() }
            CheckMenuItem(strings().comment, form.commentChecked, "set-menu-comment", {
                open = false
                viewModel.toggleComment()
            })
            form.everyone?.let { checked ->
                CheckMenuItem(strings().showEveryone, checked, "set-menu-everyone", {
                    open = false
                    viewModel.toggleEveryone()
                })
            }
        }
    }
}

@Composable
private fun CheckMenuItem(
    label: String,
    checked: Boolean,
    tag: String,
    onToggle: () -> Unit,
    icon: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .height(48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        icon()
    }
}

/** Only the active person's name is written, so the avatars of a whole family fit in view. */
@Composable
private fun PersonChip(
    person: AccountUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
    val inView = remember { BringIntoViewRequester() }
    // The active person stays in view however far along the row they are, after any switch.
    LaunchedEffect(person.active) { if (person.active) inView.bringIntoView() }
    val chip =
        Modifier
            .bringIntoViewRequester(inView)
            .height(ChipHeight)
            .clip(shape)
            .selectable(selected = person.active, onClick = onClick)
            .semantics { contentDescription = person.displayName }
            .testTag("person-${person.id.value}")
    if (!person.active) {
        PersonAvatar(person.id, person.displayName, person.avatar, chip, ChipHeight, false)
        return
    }
    Row(
        chip
            .background(colors.primary.copy(alpha = 0.14f))
            .border(1.dp, colors.primary, shape)
            .padding(start = 5.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(person.id, person.displayName, person.avatar, size = 38.dp)
        Text(
            person.displayName,
            fontSize = 15.sp,
            letterSpacing = 0.em,
            maxLines = 1,
            color = colors.onPrimaryContainer,
        )
    }
}
