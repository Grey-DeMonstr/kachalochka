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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.AccountUi
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.account.dashedCircle
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.components.Stepper
import monster.greyde.kachalochka.ui.components.TextInput
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.MachineThumbnail
import monster.greyde.kachalochka.ui.strings.strings
import kotlin.math.roundToInt

/** Frame 7c: one machine of the visit, its record, last time, friends and the day's sets. */
@Composable
internal fun MachinePage(
    page: MachinePageUi,
    viewModel: VisitViewModel,
    onOpenSettings: () -> Unit,
    onOpenMachineSettings: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Screen(
        "",
        onBack = { viewModel.closeSheet() },
        onOpenSettings = onOpenSettings,
        actions = {
            SquareIconButton(
                PhosphorIcons.Gear,
                strings().settings,
                onOpenMachineSettings,
                Modifier.testTag("machine-settings"),
            )
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().testTag("machine-page")) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .alpha(if (page.form != null) 0.45f else 1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MachineThumbnail(
                        page.photo,
                        PhosphorIcons.Barbell,
                        Modifier.testTag("page-thumb"),
                        size = 64.dp,
                        onClick = onOpenMachineSettings,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            buildAnnotatedString {
                                append(page.name)
                                page.platformSuffix?.let {
                                    withStyle(
                                        SpanStyle(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = colors.onBackground.copy(alpha = 0.55f),
                                        ),
                                    ) { append(" $it") }
                                }
                            },
                            modifier = Modifier.testTag("page-machine-name"),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onBackground,
                        )
                        if (page.tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                page.tags.forEach { TagChip(it, Modifier.testTag("page-tag-$it")) }
                            }
                        }
                        if (page.setupNote.isNotBlank()) {
                            Text(
                                page.setupNote,
                                modifier = Modifier.testTag("page-note"),
                                fontSize = 15.sp,
                                color = colors.onBackground.copy(alpha = 0.68f),
                            )
                        }
                    }
                }
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
                Column {
                    page.sets.forEach { row ->
                        SetRow(
                            row,
                            ordering = false,
                            onEdit = viewModel::editSet,
                            Modifier
                                .clip(ControlShape)
                                .background(colors.onBackground.copy(alpha = 0.04f)),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        strings().add,
                        PhosphorIcons.Plus,
                        viewModel::openForm,
                        Modifier.weight(1f).testTag("add-set"),
                    )
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
            page.form?.let {
                SetForm(
                    form = it,
                    viewModel = viewModel,
                    onAddAccount = onAddAccount,
                    modifier = Modifier.align(Alignment.BottomCenter),
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
    onAddAccount: () -> Unit,
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
        if (form.people.size > 1) {
            PersonChips(form.people, viewModel::switchTo, onAddAccount)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        TextInput(
            form.comment,
            strings().comment,
            viewModel::typeComment,
            Modifier.testTag("set-comment-field"),
            KeyboardOptions.Default,
            singleLine = false,
        )
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

/** Frame 5g: one tap moves the form between the people signed in, the last chip adds one. */
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
                strings().addAccount,
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
            .padding(start = 5.dp, end = 14.dp)
            .testTag("person-${person.id.value}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            person.id,
            person.displayName,
            person.avatar,
            size = 38.dp,
            accent = person.active,
        )
        Text(
            person.displayName,
            fontSize = 15.sp,
            letterSpacing = 0.em,
            color = if (person.active) colors.onPrimaryContainer else colors.onBackground,
        )
    }
}
