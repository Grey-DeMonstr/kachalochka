package monster.greyde.kachalochka.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.StatsSort
import monster.greyde.kachalochka.ui.components.ChipRow
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.components.TagText
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.MachineThumbnail
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.gainColor
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val CardShape = RoundedCornerShape(12.dp)

/** Opens on [initial], or on "Общая" when it is null. */
@Composable
fun StatisticsScreen(
    initial: MachineId?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVisit: (CalendarDay, MachineId) -> Unit = { _, _ -> },
) {
    val viewModel: StatisticsViewModel = koinViewModel { parametersOf(initial) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    var searching by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun choose(machine: MachineId?) {
        focusManager.clearFocus()
        searching = false
        viewModel.choose(machine)
    }

    fun endSearch() {
        focusManager.clearFocus()
        searching = false
        viewModel.search("")
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = searching,
        onBackCompleted = { endSearch() },
    )
    Screen(
        strings().statistics,
        onBack = { if (searching) endSearch() else onBack() },
        onOpenSettings = onOpenSettings,
        actions = {
            if (state.selected == null && state.overall.isNotEmpty()) {
                SquareIconButton(
                    PhosphorIcons.ShareNetwork,
                    strings().export,
                    viewModel::export,
                    Modifier.testTag("stats-export"),
                )
            }
        },
    ) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = ChoiceTop),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChoiceField(
                        state.selected,
                        searching,
                        onOpen = { searching = true },
                        onSearch = viewModel::search,
                        onCancel = { endSearch() },
                    )
                    PeriodChips(state.period, viewModel::choosePeriod)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    val machine = state.machine
                    if (machine == null) {
                        Overall(
                            state,
                            viewModel::choose,
                            viewModel::chooseSort,
                            viewModel::toggleGroupByTag,
                            viewModel::toggleImprovementsOnly,
                            viewModel::dismissNotice,
                        )
                    } else {
                        val id = state.selected?.id
                        MachineStats(
                            machine,
                            onOpenDay = id?.let { { day -> onOpenVisit(day, it) } },
                        )
                    }
                }
            }
            if (searching) {
                val listTop = ChoiceTop + ChoiceHeight + 4.dp
                // A tap beside the list ends the search instead of reaching what lies under it.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = listTop)
                        .pointerInput(Unit) { detectTapGestures { endSearch() } },
                )
                ChoiceList(
                    state,
                    ::choose,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = listTop),
                )
            }
        }
    }
}

private val ChoiceTop = 14.dp
private val ChoiceHeight = 56.dp
private val ListShape = RoundedCornerShape(10.dp)

/** The chosen machine; while [searching], the search over the machines instead. */
@Composable
private fun ChoiceField(
    selected: StatsChoiceUi?,
    searching: Boolean,
    onOpen: () -> Unit,
    onSearch: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(ChoiceHeight)
            .clip(BoxShape)
            .border(1.dp, if (searching) colors.primary else colors.outlineVariant, BoxShape)
            .clickable(enabled = !searching, onClick = onOpen)
            .padding(
                start = if (searching) 14.dp else 10.dp,
                end = if (searching) 7.dp else 14.dp,
            ).testTag("stats-choice"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (searching) {
            Icon(
                PhosphorIcons.MagnifyingGlass,
                null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            SearchInput(onSearch, Modifier.weight(1f))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCancel)
                    .testTag("stats-search-cancel"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.X,
                    strings().cancelSearch,
                    tint = colors.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            ChoiceThumbnail(selected?.photo, overall = selected == null)
            Text(
                selected?.name ?: strings().overallStats,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.onBackground,
            )
            Icon(
                PhosphorIcons.MagnifyingGlass,
                null,
                tint = colors.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Starts empty and focused each time the search opens. */
@Composable
private fun SearchInput(
    onSearch: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val field = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val currentOnSearch by rememberUpdatedState(onSearch)
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { currentOnSearch(it) }
    }
    BasicTextField(
        state = field,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = TextStyle(fontSize = 17.sp, color = colors.onBackground),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.focusRequester(focus).testTag("stats-search"),
    )
}

@Composable
private fun ChoiceList(
    state: StatisticsUiState,
    onChoose: (MachineId?) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val chosen = state.selected?.id
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .shadow(8.dp, ListShape)
            .clip(ListShape)
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, ListShape)
            .verticalScroll(rememberScrollState())
            .padding(6.dp)
            .testTag("stats-choice-list"),
    ) {
        if (state.showsOverall) {
            ChoiceRow(null, AnnotatedString(strings().overallStats), chosen == null, "overall") {
                onChoose(null)
            }
        }
        state.choiceSections.forEach { section ->
            section.title?.let { SectionTitle(it, "stats-choice-section-$it", menu = true) }
            section.items.forEach { choice ->
                ChoiceRow(
                    choice.photo,
                    highlighted(choice),
                    choice.id == chosen,
                    choice.id.value,
                    machine = true,
                ) { onChoose(choice.id) }
            }
        }
        if (state.nothingFound) {
            Text(
                strings().nothingFound,
                modifier = Modifier.padding(12.dp).testTag("stats-choice-empty"),
                fontSize = 15.sp,
                color = colors.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun highlighted(choice: StatsChoiceUi): AnnotatedString {
    val found = choice.matched ?: return AnnotatedString(choice.name)
    val style =
        SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    return buildAnnotatedString {
        append(choice.name)
        addStyle(style, found.first, found.last + 1)
    }
}

/** [key] names the row's test tags: the machine's id, or "overall". */
@Composable
private fun ChoiceRow(
    photo: Photo?,
    name: AnnotatedString,
    chosen: Boolean,
    key: String,
    machine: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(BoxShape)
            .then(
                if (chosen) Modifier.background(colors.primary.copy(alpha = 0.08f)) else Modifier,
            ).clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag("stats-choice-$key"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChoiceThumbnail(photo, overall = !machine, size = 34)
        Text(
            name,
            modifier = Modifier.weight(1f).testTag("stats-choice-name-$key"),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.onBackground,
        )
        if (chosen) {
            Icon(
                PhosphorIcons.Check,
                null,
                tint = colors.secondary,
                modifier = Modifier.size(18.dp).testTag("stats-choice-check-$key"),
            )
        }
    }
}

@Composable
private fun ChoiceThumbnail(
    photo: Photo?,
    overall: Boolean,
    size: Int = 36,
) {
    MachineThumbnail(
        photo,
        if (overall) PhosphorIcons.ChartLineUp else PhosphorIcons.Barbell,
        size = size.dp,
    )
}

@Composable
private fun Overall(
    state: StatisticsUiState,
    onChoose: (MachineId) -> Unit,
    onSort: (StatsSort) -> Unit,
    onToggleGroupByTag: () -> Unit,
    onToggleImprovementsOnly: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val s = strings()
    ChipRow(
        listOf(
            StatsSort.Recent to s.recent,
            StatsSort.Name to s.sortName,
            StatsSort.Frequent to s.sortFrequent,
            StatsSort.Growth to s.sortGrowth,
        ),
        state.sort,
        onSort,
        Modifier.padding(bottom = 12.dp),
    ) { "stats-sort-${it.name.lowercase()}" }
    if (state.canGroupByTag) {
        CheckRow(s.groupByTag, state.groupByTag, onToggleGroupByTag, "stats-group-by-tag")
    }
    CheckRow(
        s.improvementsOnly,
        state.improvementsOnly,
        onToggleImprovementsOnly,
        "stats-improvements-only",
    )
    state.notice?.let {
        Text(
            it,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismissNotice)
                    .padding(bottom = 12.dp)
                    .testTag("stats-notice"),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
    SectionLabel(
        state.overallTitle,
        Modifier.padding(bottom = 10.dp).testTag("stats-overall-title"),
    )
    if (state.overall.isEmpty()) {
        Muted(
            if (state.nothingImproved) s.noImprovements else s.noSetsInPeriod,
            Modifier.testTag("stats-empty"),
        )
    }
    state.overallSections.forEach { section ->
        section.title?.let { SectionTitle(it, "stats-section-$it") }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section.items.forEach { card -> ProgressCard(card) { onChoose(card.id) } }
        }
    }
}

@Composable
private fun CheckRow(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    tag: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .toggleable(value = checked, onValueChange = { onToggle() })
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

/** A tag group's heading, drawn as the visit's; in the [menu] it is indented like its items. */
@Composable
private fun SectionTitle(
    title: String,
    tag: String,
    menu: Boolean = false,
) {
    TagText(
        title,
        modifier =
            Modifier
                .padding(
                    start = if (menu) 12.dp else 0.dp,
                    end = if (menu) 12.dp else 0.dp,
                    top = 14.dp,
                    bottom = 8.dp,
                ).testTag(tag),
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun ProgressCard(
    card: ProgressCardUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val id = card.id.value
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, colors.outlineVariant, CardShape)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("stats-card-$id"),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MachineThumbnail(card.photo, PhosphorIcons.Barbell, size = 44.dp)
            Text(card.name, Modifier.weight(1f), fontSize = 16.sp, color = colors.onBackground)
            ChangeChip(card.change, card.improved, "stats-change-$id")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SetBox(card.fromLabel, card.from, Modifier.weight(1f).testTag("stats-from-$id"))
            Icon(
                PhosphorIcons.ArrowRight,
                null,
                tint = colors.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp),
            )
            SetBox(card.toLabel, card.to, Modifier.weight(1f).testTag("stats-to-$id"))
        }
    }
}

@Composable
private fun ChangeChip(
    text: String,
    improved: Boolean,
    tag: String,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (improved) gainColor() else colors.onBackground.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .height(28.dp)
            .clip(shape)
            .border(1.dp, tint.copy(alpha = 0.5f), shape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            modifier = Modifier.testTag(tag),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun SetBox(
    label: String,
    value: String,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(BoxShape)
            .background(colors.onBackground.copy(alpha = 0.04f))
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        SectionLabel(label)
        Text(
            value,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 15.sp,
            maxLines = 1,
            color = colors.onBackground,
        )
    }
}
