package monster.greyde.kachalochka.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.StatsPeriod
import monster.greyde.kachalochka.core.domain.gym.StatsSort
import monster.greyde.kachalochka.ui.components.ChipRow
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.MachineThumbnail
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.gainColor
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val CardShape = RoundedCornerShape(12.dp)
private val BoxShape = RoundedCornerShape(8.dp)

/** Opens on [initial], or on "Общая" when it is null. */
@Composable
fun StatisticsScreen(
    initial: MachineId?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: StatisticsViewModel = koinViewModel { parametersOf(initial) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Screen(strings().statistics, onBack = onBack, onOpenSettings = onOpenSettings) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MachineChoice(state, viewModel::choose)
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
                )
            } else {
                MachineStats(machine)
            }
        }
    }
}

@Composable
private fun MachineChoice(
    state: StatisticsUiState,
    onChoose: (MachineId?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val selected = state.selected
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(BoxShape)
                .border(1.dp, if (open) colors.primary else colors.outlineVariant, BoxShape)
                .clickable { open = true }
                .padding(start = 10.dp, end = 14.dp)
                .testTag("stats-choice"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                PhosphorIcons.CaretDown,
                null,
                tint = colors.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ChoiceItem(
                null,
                strings().overallStats,
                null,
                selected == null,
                "stats-choice-overall",
            ) {
                open = false
                onChoose(null)
            }
            state.choiceSections.forEach { section ->
                section.title?.let { SectionTitle(it, "stats-choice-section-$it", menu = true) }
                section.items.forEach { choice ->
                    ChoiceItem(
                        choice.photo,
                        choice.name,
                        choice.id,
                        choice.id == selected?.id,
                        "stats-choice-${choice.id.value}",
                    ) {
                        open = false
                        onChoose(choice.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceItem(
    photo: Photo?,
    name: String,
    id: MachineId?,
    chosen: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = { ChoiceThumbnail(photo, overall = id == null, size = 32) },
        trailingIcon = {
            if (chosen) {
                Icon(
                    PhosphorIcons.Check,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        modifier = Modifier.testTag(tag),
    )
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
private fun PeriodChips(
    chosen: StatsPeriod,
    onChoose: (StatsPeriod) -> Unit,
) {
    val s = strings()
    ChipRow(
        listOf(
            StatsPeriod.Month to s.statsMonth,
            StatsPeriod.ThreeMonths to s.statsThreeMonths,
            StatsPeriod.SixMonths to s.statsSixMonths,
            StatsPeriod.Year to s.statsYear,
        ),
        chosen,
        onChoose,
    ) { "period-${it.name.lowercase()}" }
}

@Composable
private fun Overall(
    state: StatisticsUiState,
    onChoose: (MachineId) -> Unit,
    onSort: (StatsSort) -> Unit,
    onToggleGroupByTag: () -> Unit,
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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .toggleable(value = state.groupByTag, onValueChange = { onToggleGroupByTag() })
                .testTag("stats-group-by-tag"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.groupByTag, onCheckedChange = null)
            Text(
                s.groupByTag,
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
    SectionLabel(
        state.overallTitle,
        Modifier.padding(bottom = 10.dp).testTag("stats-overall-title"),
    )
    if (state.overall.isEmpty()) {
        Muted(s.noSetsInPeriod, Modifier.testTag("stats-empty"))
    }
    state.overallSections.forEach { section ->
        section.title?.let { SectionTitle(it, "stats-section-$it") }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section.items.forEach { card -> ProgressCard(card) { onChoose(card.id) } }
        }
    }
}

/** A tag group's heading, drawn as the visit's; in the [menu] it is indented like its items. */
@Composable
private fun SectionTitle(
    title: String,
    tag: String,
    menu: Boolean = false,
) {
    Text(
        title,
        modifier =
            Modifier
                .padding(
                    start = if (menu) 12.dp else 0.dp,
                    end = if (menu) 12.dp else 0.dp,
                    top = 14.dp,
                    bottom = 8.dp,
                ).testTag(tag),
        fontSize = 13.sp,
        letterSpacing = 0.09.em,
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

@Composable
private fun MachineStats(machine: MachineStatsUi) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel(machine.title, Modifier.weight(1f).testTag("stats-chart-title"))
        machine.best?.let {
            Text(
                it,
                modifier = Modifier.testTag("stats-best"),
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.7f),
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(BoxShape)
            .border(1.dp, colors.outlineVariant, BoxShape)
            .padding(start = 4.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (machine.points.isEmpty()) {
            Muted(strings().noSetsInPeriod, Modifier.testTag("stats-empty"))
        } else {
            StatsChart(machine.points, machine.start, machine.end, machine.zeroOnTop)
        }
    }
    SectionLabel(strings().allResults, Modifier.padding(top = 24.dp, bottom = 8.dp))
    machine.history.forEachIndexed { i, row ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                row.date,
                modifier = Modifier.width(112.dp).testTag("stats-history-date-$i"),
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.55f),
            )
            Text(
                row.results,
                modifier = Modifier.testTag("stats-history-results-$i"),
                fontSize = 15.sp,
                color = colors.onBackground,
            )
        }
    }
}

@Composable
private fun Muted(
    text: String,
    modifier: Modifier,
) {
    Text(
        text,
        modifier = modifier,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    )
}
