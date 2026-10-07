package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.ui.components.ChoiceChip
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** A null [day] picks for a plan, where the copy row is not offered. */
@Composable
fun MachinePickerScreen(
    day: CalendarDay?,
    selectedMachineId: MachineId?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onPicked: (MachineId) -> Unit,
    onCreate: (name: String, tags: List<String>) -> Unit,
    onCopy: (source: MachineId, name: String) -> Unit,
) {
    val viewModel: MachinePickerViewModel = koinViewModel { parametersOf(day) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Screen(strings().machine, onBack = onBack, onOpenSettings = onOpenSettings) {
        SearchBar(state.query, viewModel::onQueryChange, rule = false)
        SortChips(state.sort, viewModel::chooseSort, Modifier.padding(bottom = 12.dp))
        if (state.tags.isNotEmpty()) TagFilter(state.tags, viewModel::toggleTag) else Rule()
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            state.createLabel?.let { label ->
                CreateRow(label, state.createHint) {
                    onCreate(state.query.trim(), viewModel.createTags)
                }
            }
            if (state.nothingFound) {
                Text(
                    strings().nothingFound,
                    modifier = Modifier.padding(16.dp).testTag("picker-nothing"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
            if (state.rows.isNotEmpty()) {
                SectionLabel(
                    state.sectionLabel,
                    modifier =
                        Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                            .testTag("picker-section"),
                )
            }
            state.rows.forEach { card ->
                MachineCard(card, "machine-row-${card.id.value}") { onPicked(card.id) }
            }
            state.friendSections.forEach { section ->
                FriendSectionHeader(
                    section,
                    Modifier.testTag("picker-friend-${section.friend.userId.value}"),
                )
                section.cards.forEach { card ->
                    MachineCard(card, "friend-machine-${card.id.value}") {
                        viewModel.pickFriend(card.id, onPicked)
                    }
                }
            }
            state.inVisitRows.forEach { card ->
                MachineCard(card, "machine-row-${card.id.value}") { onPicked(card.id) }
            }
            if (selectedMachineId != null && day != null) {
                SectionLabel(
                    strings().basedOnExisting,
                    modifier =
                        Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 20.dp,
                            bottom = 8.dp,
                        ),
                )
                CopyRow(onClick = { onCopy(selectedMachineId, state.query.trim()) })
            }
        }
    }
}

/** Tags under the search field; the chosen ones narrow the list. */
@Composable
internal fun TagFilter(
    tags: List<TagChoiceUi>,
    onToggle: (String) -> Unit,
    testTagPrefix: String = "picker-tag",
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach {
                ChoiceChip(
                    it.name,
                    it.chosen,
                    "$testTagPrefix-${it.name}",
                ) { onToggle(it.name) }
            }
        }
        Rule()
    }
}

@Composable
internal fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    rule: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    // The field owns its text, so a clear goes through the keyboard's session, not around it.
    val field = rememberTextFieldState(query)
    val currentQuery by rememberUpdatedState(query)
    val currentOnChange by rememberUpdatedState(onQueryChange)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(query) {
        if (field.text.toString() != query) field.setTextAndPlaceCursorAtEnd(query)
    }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect {
            if (it != currentQuery) currentOnChange(it)
        }
    }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(56.dp)
                .clip(shape)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.primary, shape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                PhosphorIcons.MagnifyingGlass,
                null,
                tint = colors.secondary,
                modifier = Modifier.size(22.dp),
            )
            BasicTextField(
                state = field,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = TextStyle(fontSize = 17.sp, color = colors.onBackground),
                cursorBrush = SolidColor(colors.secondary),
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("machine-search"),
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .focusProperties { canFocus = false }
                        .clickable {
                            field.clearText()
                            focus.requestFocus()
                            keyboard?.show()
                        }.testTag("clear-search"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PhosphorIcons.X,
                        strings().clearSearch,
                        tint = colors.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (rule) Rule()
    }
}

@Composable
private fun CreateRow(
    label: String,
    hint: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.primary.copy(alpha = 0.10f))
                .clickable(onClick = onClick)
                .padding(16.dp)
                .testTag("create-machine"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(ControlShape)
                    .border(1.dp, colors.primary, ControlShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.Plus,
                    null,
                    tint = colors.secondary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onPrimaryContainer,
                )
                Text(
                    hint,
                    modifier = Modifier.testTag("create-hint"),
                    fontSize = 13.sp,
                    color = colors.secondary,
                )
            }
            Icon(
                PhosphorIcons.CaretRight,
                null,
                tint = colors.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
        }
        Rule()
    }
}

@Composable
private fun CopyRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("copy-machine"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(ControlShape)
                .border(1.dp, colors.onBackground.copy(alpha = 0.22f), ControlShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.Copy,
                null,
                tint = colors.secondary,
                modifier = Modifier.size(26.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(strings().copyMachine, fontSize = 17.sp, color = colors.onBackground)
            Text(
                strings().copyKeeps,
                fontSize = 13.sp,
                color = colors.onBackground.copy(alpha = 0.52f),
            )
        }
        Icon(
            PhosphorIcons.CaretRight,
            null,
            tint = colors.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
    }
}
