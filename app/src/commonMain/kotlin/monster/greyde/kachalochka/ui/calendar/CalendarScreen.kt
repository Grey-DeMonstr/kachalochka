package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.traineeActive
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SegmentRow
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.plans.VisitPlacementNotice
import monster.greyde.kachalochka.ui.plans.VisitPlacementViewModel
import monster.greyde.kachalochka.ui.plans.VisitPlanChooser
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVisit: (CalendarDay) -> Unit,
    onOpenFriendVisit: (UserId, String, CalendarDay) -> Unit,
    onSaveAsPlan: (CalendarDay) -> Unit,
) {
    val viewModel: CalendarViewModel = koinViewModel()
    val placement: VisitPlacementViewModel = koinViewModel()
    val modes: VisitsModePreference = koinInject()
    val mode by modes.mode.collectAsState()
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = mode == VisitsMode.Calendar && state?.moving == true,
        onBackCompleted = { viewModel.cancelMove() },
    )

    fun show(next: VisitsMode) {
        viewModel.cancelMove()
        scope.launch { modes.set(next) }
    }
    Screen(
        strings().visits,
        onBack = { if (mode != VisitsMode.Calendar || !viewModel.cancelMove()) onBack() },
        onOpenSettings = onOpenSettings,
    ) {
        SegmentRow(
            listOf(
                Choice(
                    strings().calendarMode,
                    "mode-calendar",
                    icon = PhosphorIcons.CalendarBlank,
                ),
                Choice(strings().feedMode, "mode-feed", icon = PhosphorIcons.Rows),
            ),
            selected = mode.ordinal,
            onSelect = { show(VisitsMode.entries[it]) },
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp),
        )
        when (mode) {
            VisitsMode.Calendar ->
                CalendarMode(viewModel, placement, onOpenVisit, onOpenFriendVisit)

            VisitsMode.Feed ->
                FeedMode(placement, onOpenVisit, onOpenFriendVisit) { day, visit ->
                    show(VisitsMode.Calendar)
                    viewModel.openDay(day)
                    viewModel.startMove(visit)
                }
        }
        VisitPlanChooser(placement, onSaveAsPlan)
    }
}

@Composable
private fun ColumnScope.CalendarMode(
    viewModel: CalendarViewModel,
    placement: VisitPlacementViewModel,
    onOpenVisit: (CalendarDay) -> Unit,
    onOpenFriendVisit: (UserId, String, CalendarDay) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    // A trainer looks at a trainee's visits and plans from them, nothing more.
    val readOnly = traineeActive()
    val current = state ?: return
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthHeader(
            current.monthTitle,
            current.canShowNextMonth,
            onPrevious = { viewModel.showMonth(-1) },
            onNext = { viewModel.showMonth(+1) },
        )
        if (current.moving) MoveBanner(onCancel = { viewModel.cancelMove() })
        MonthGrid(current.weeks, onSelect = viewModel::selectDay)
        Rule()
        Text(
            current.dayTitle,
            modifier = Modifier.testTag("calendar-day-title"),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        current.visit?.let { card ->
            val visit = checkNotNull(card.own).id
            VisitCard(
                card,
                onOpen = { onOpenVisit(current.day) },
                menu =
                    VisitMenuActions(
                        onMove = { viewModel.startMove(visit) }.takeUnless { readOnly },
                        onToPlan = { placement.choosePlan(current.day) },
                        onRepeatToday =
                            { viewModel.repeatToday(onOpenVisit) }.takeUnless { readOnly },
                        onRemove = { viewModel.askToRemove(visit) }.takeUnless { readOnly },
                    ).takeIf { !current.moving },
            )
            VisitPlacementNotice(placement, current.day)
        }
        if (current.noVisit) {
            Text(
                strings().noVisit,
                modifier = Modifier.testTag("calendar-empty"),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
            if (!readOnly) {
                AccentButton(
                    strings().addVisit,
                    PhosphorIcons.Plus,
                    { onOpenVisit(current.day) },
                    Modifier.testTag("add-visit"),
                )
            }
        }
        current.friendVisits.forEach { card ->
            VisitCard(card, onOpen = { card.openAsFriend(onOpenFriendVisit) })
        }
    }
    current.removal?.let {
        ConfirmDialog(
            it.title,
            it.text,
            strings().delete,
            "confirm-remove",
            "cancel-remove",
            onConfirm = viewModel::confirmRemoval,
            onCancel = viewModel::cancelRemoval,
        )
    }
    current.replacement?.let {
        ConfirmDialog(
            it.title,
            it.text,
            strings().replace,
            "confirm-replace",
            "cancel-replace",
            onConfirm = viewModel::confirmReplacement,
            onCancel = viewModel::cancelReplacement,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.FeedMode(
    placement: VisitPlacementViewModel,
    onOpenVisit: (CalendarDay) -> Unit,
    onOpenFriendVisit: (UserId, String, CalendarDay) -> Unit,
    onMove: (CalendarDay, VisitId) -> Unit,
) {
    val feed: VisitFeedViewModel = koinViewModel()
    val state by feed.state.collectAsState()
    LaunchedEffect(Unit) { feed.refresh() }
    val readOnly = traineeActive()
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow {
            val layout = list.layoutInfo
            (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) to layout.totalItemsCount
        }.collect { (last, total) -> if (last >= total - 3) feed.loadMore() }
    }
    val current = state ?: return
    if (current.people.isNotEmpty()) FeedPeopleRow(current.people, feed::select)
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth().testTag("visit-feed"),
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
    ) {
        current.days.forEach { day ->
            day.month?.let { month ->
                item(key = "month-${day.day.iso}") { MonthLabel(month, day.day) }
            }
            stickyHeader(key = "day-${day.day.iso}") { DayHeading(day) }
            items(day.cards, key = { it.tag }) { card ->
                Column(
                    Modifier.padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val own = card.own
                    if (own == null) {
                        VisitCard(card, onOpen = { card.openAsFriend(onOpenFriendVisit) })
                    } else {
                        VisitCard(
                            card,
                            onOpen = { onOpenVisit(card.day) },
                            menu =
                                VisitMenuActions(
                                    onMove = { onMove(card.day, own.id) }.takeUnless { readOnly },
                                    onToPlan = { placement.choosePlan(card.day) },
                                    onRepeatToday =
                                        { feed.repeatToday(card.day, onOpenVisit) }
                                            .takeUnless { readOnly },
                                    onRemove =
                                        {
                                            feed.askToRemove(
                                                own.id,
                                            )
                                        }.takeUnless { readOnly },
                                ),
                        )
                        VisitPlacementNotice(placement, card.day)
                    }
                }
            }
        }
        when {
            current.loading -> item(key = "loading") { LoadingDay() }
            current.ended ->
                item(key = "end") {
                    val empty = current.days.isEmpty()
                    val end = Modifier.fillMaxWidth().testTag("feed-end")
                    Text(
                        if (empty) strings().noVisitsYet else strings().firstVisit,
                        modifier = end.padding(vertical = 28.dp),
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    )
                }
        }
    }
    current.removal?.let {
        ConfirmDialog(
            it.title,
            it.text,
            strings().delete,
            "confirm-remove",
            "cancel-remove",
            onConfirm = feed::confirmRemoval,
            onCancel = feed::cancelRemoval,
        )
    }
}

private fun VisitCardUi.openAsFriend(open: (UserId, String, CalendarDay) -> Unit) =
    open(checkNotNull(person.userId), person.name, day)

@Composable
private fun MonthLabel(
    text: String,
    day: CalendarDay,
) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(top = 22.dp, bottom = 2.dp).testTag("feed-month-${day.iso}"),
        fontSize = 12.sp,
        letterSpacing = 0.1.em,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun DayHeading(day: FeedDayUi) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(top = 14.dp, bottom = 10.dp)
            .semantics(mergeDescendants = true) { heading() }
            .testTag("feed-day-${day.day.iso}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            day.title,
            modifier = Modifier.weight(1f).alignByBaseline(),
            fontSize = 16.5.sp,
            color = colors.onBackground,
        )
        day.aside?.let {
            Text(
                it,
                modifier = Modifier.alignByBaseline(),
                fontSize = 13.sp,
                color = colors.onBackground.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun LoadingDay() {
    val fill = MaterialTheme.colorScheme.onBackground
    Column(
        Modifier.padding(top = 18.dp).testTag("feed-loading"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.45f)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(fill.copy(alpha = 0.06f)),
        )
        listOf(0.05f, 0.04f).forEach {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fill.copy(alpha = it)),
            )
        }
    }
}

@Composable
private fun MoveBanner(onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().testTag("move-banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            strings().chooseNewDay,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            color = colors.secondary,
        )
        Text(
            strings().cancel,
            modifier = Modifier.clickable(onClick = onCancel).padding(8.dp).testTag("cancel-move"),
            fontSize = 15.sp,
            color = colors.onBackground,
        )
    }
}
