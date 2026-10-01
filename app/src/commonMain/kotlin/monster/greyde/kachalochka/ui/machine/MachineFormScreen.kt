package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.ChoiceChip
import monster.greyde.kachalochka.ui.components.ChoiceRow
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.components.dashedBorder
import monster.greyde.kachalochka.ui.format.unitLabel
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.PhotoCapture
import monster.greyde.kachalochka.ui.photos.PhotoStrip
import monster.greyde.kachalochka.ui.photos.PhotoViewer
import monster.greyde.kachalochka.ui.photos.ShownPhoto
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.koinInject
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
    onOpenFriendMachine: (MachineId, UserId) -> Unit = { _, _ -> },
) {
    val viewModel: MachineFormViewModel = koinViewModel { parametersOf(args) }
    val state by viewModel.state.collectAsState()
    val linking by viewModel.linking.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val capture: PhotoCapture = koinInject()
    val launchers = capture.rememberLaunchers(viewModel::addPhoto)
    var opened by remember { mutableStateOf<ShownPhoto?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }
    Screen(
        strings().machine,
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
            FieldLabel(strings().name)
            FormField(
                value = state.name,
                onValueChange = { name -> viewModel.update { it.copy(name = name) } },
                tag = "machine-name",
                minHeight = 50.dp,
                fontSize = 18.sp,
                singleLine = true,
            )
            if (photos.isEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhotoStrip(photos, launchers, onOpen = { opened = it })
                    if (linking.linkedWith.isNotEmpty()) {
                        LinkedWith(linking.linkedWith, onOpenFriendMachine)
                    }
                }
            } else {
                PhotoStrip(photos, launchers, { opened = it }, Modifier.fillMaxWidth())
                if (linking.linkedWith.isNotEmpty()) {
                    LinkedWith(linking.linkedWith, onOpenFriendMachine)
                }
            }
            FieldLabel(strings().setupNote)
            FormField(
                value = state.setupNote,
                onValueChange = { note -> viewModel.update { it.copy(setupNote = note) } },
                tag = "machine-note",
                minHeight = 62.dp,
                fontSize = 15.sp,
                singleLine = false,
            )
            TagsSection(state, viewModel)
            FieldLabel(strings().howWeightCounts)
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
                FieldLabel(strings().unitName)
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
            if (linking.canLink) {
                OutlineButton(
                    strings().linkTo,
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
                strings().save,
                PhosphorIcons.Check,
                { viewModel.save(onSaved) },
                Modifier.testTag("save-machine"),
                enabled = state.canSave,
            )
        }
    }
    opened?.let { photo ->
        PhotoViewer(
            photo,
            onClose = { opened = null },
            onDelete =
                {
                    viewModel.removePhoto(photo.key)
                    opened = null
                }.takeIf { photo.owner == null },
            onMakeCover =
                {
                    viewModel.makeCover(photo.key)
                    opened = null
                }.takeIf { !photo.cover },
        )
    }
    if (linking.confirmingUnlink) {
        ConfirmDialog(
            title = strings().unlinkTitle,
            text = strings().unlinkText,
            confirmLabel = strings().unlink,
            confirmTag = "confirm-unlink",
            cancelTag = "cancel-unlink",
            onConfirm = viewModel::confirmUnlink,
            onCancel = viewModel::cancelUnlink,
        )
    }
}

@Composable
private fun LinkedWith(
    linked: List<LinkedMachineUi>,
    onOpen: (MachineId, UserId) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.tertiary))
    val s = strings()
    val text =
        buildAnnotatedString {
            append(s.linkedWith)
            linked.forEachIndexed { index, machine ->
                if (index > 0) append(", ")
                val link =
                    LinkAnnotation.Clickable(machine.machineId.value, linkStyle) {
                        onOpen(machine.machineId, machine.ownerId)
                    }
                withLink(link) { append(machine.label) }
            }
        }
    Text(
        text,
        modifier = Modifier.testTag("machine-linked-with"),
        fontSize = 13.sp,
        color = colors.onBackground.copy(alpha = 0.60f),
    )
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
private fun TagsSection(
    state: MachineFormState,
    viewModel: MachineFormViewModel,
) {
    val colors = MaterialTheme.colorScheme
    FieldLabel(strings().tags)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.shownTags.forEach { tag ->
            ChoiceChip(tag, tag in state.tags, "tag-$tag") { viewModel.toggleTag(tag) }
        }
        NewTagChip(state.newTag, viewModel::typeNewTag, viewModel::addNewTag)
    }
    val offered = state.offeredFriendTags
    if (offered.isNotEmpty()) {
        Text(
            strings().friendsTags,
            fontSize = 12.sp,
            color = colors.onBackground.copy(alpha = 0.48f),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            offered.forEach { FriendTagChip(it) { viewModel.toggleTag(it.tag) } }
        }
    }
}

/** A chip-shaped field adding the tag typed in it, on done or on its "+". */
@Composable
private fun NewTagChip(
    text: String,
    onType: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .height(38.dp)
            .clip(shape)
            .border(1.dp, colors.onBackground.copy(alpha = 0.18f), shape)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 72.dp)) {
            if (text.isEmpty()) {
                Text(
                    strings().newTag,
                    fontSize = 15.sp,
                    color = colors.onBackground.copy(alpha = 0.45f),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onType,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = colors.onBackground),
                cursorBrush = SolidColor(colors.secondary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                modifier =
                    Modifier
                        .width(
                            IntrinsicSize.Min,
                        ).widthIn(min = 72.dp)
                        .testTag("new-tag"),
            )
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onAdd)
                .testTag("add-tag"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.Plus,
                strings().add,
                tint = colors.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** A friend's tag the account lacks: dashed, with the friend's avatar, added with a tap. */
@Composable
private fun FriendTagChip(
    offered: FriendTagUi,
    onAdd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .height(38.dp)
            .clip(shape)
            .dashedBorder(colors.onBackground.copy(alpha = 0.35f), 20.dp)
            .clickable(onClick = onAdd)
            .padding(start = 6.dp, end = 12.dp)
            .testTag("friend-tag-${offered.tag}"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            offered.friend.userId,
            offered.friend.displayName,
            offered.friend.avatar,
            size = 24.dp,
        )
        Text(offered.tag, fontSize = 15.sp, color = colors.onBackground)
        Icon(
            PhosphorIcons.Plus,
            null,
            tint = colors.secondary,
            modifier = Modifier.size(14.dp),
        )
    }
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
                Choice(strings().choiceTotal, "mode-total"),
                Choice(strings().choicePerSide, "mode-per-side"),
                Choice(strings().choiceCounterweight, "mode-counterweight"),
            ),
        selected = modes.indexOf(state.weightMode),
        onSelect = { onSelect(modes[it]) },
    )
    if (state.weightMode == WeightMode.Counterweight) {
        Text(
            strings().counterweightHint,
            modifier = Modifier.testTag("mode-counterweight-hint"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
        )
    }
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
        Text(strings().platformWeight, fontSize = 15.sp, color = colors.onBackground)
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
            Text(strings().addToRecord, fontSize = 15.sp, color = colors.onBackground)
        }
        Text(
            if (included) {
                strings().platformIncludedHint
            } else {
                strings().platformApartHint
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
                Choice(strings().kg, "unit-kg"),
                Choice("lb", "unit-lb"),
                Choice(strings().customUnit, "unit-custom", weight = 2f),
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
            strings().weightStep,
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
            strings().afterSaveHint,
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
            strings().more,
            { expanded = true },
            Modifier.testTag("machine-menu"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(strings().unlinkFromFriends) },
                onClick = {
                    expanded = false
                    onUnlink()
                },
                modifier = Modifier.testTag("unlink-machine"),
            )
        }
    }
}
