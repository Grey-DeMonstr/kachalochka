package monster.greyde.kachalochka.ui.calendar

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.friendColor
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
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val current = state
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = current?.moving == true,
        onBackCompleted = { viewModel.cancelMove() },
    )
    Screen(
        strings().visits,
        onBack = { if (!viewModel.cancelMove()) onBack() },
        onOpenSettings = onOpenSettings,
    ) {
        if (current == null) return@Screen
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
            current.visit?.let { visit ->
                VisitCard(
                    visit,
                    current.me,
                    editable = !current.moving,
                    onOpen = { onOpenVisit(current.day) },
                    onMove = { viewModel.startMove(visit.id) },
                    onSaveAsPlan = { onSaveAsPlan(current.day) },
                    onRemove = { viewModel.askToRemove(visit.id) },
                )
            }
            if (current.noVisit) {
                Text(
                    strings().noVisit,
                    modifier = Modifier.testTag("calendar-empty"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                AccentButton(
                    strings().addVisit,
                    PhosphorIcons.Plus,
                    { onOpenVisit(current.day) },
                    Modifier.testTag("add-visit"),
                )
            }
            current.friendVisits.forEach { friend ->
                FriendVisitCard(
                    friend,
                    onOpen = { onOpenFriendVisit(friend.userId, friend.name, friend.day) },
                )
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

@Composable
private fun FriendVisitCard(
    visit: FriendDayVisitUi,
    onOpen: () -> Unit,
) {
    DayCard(
        tag = "friend-visit-${visit.userId.value}",
        avatar = {
            PersonAvatar(
                visit.userId,
                visit.name,
                visit.avatar,
                size = 32.dp,
                tint = friendColor(visit.color),
            )
        },
        tags = visit.tags,
        machines = visit.machines,
        onOpen = onOpen,
    )
}

/**
 * Frame 7m: the own visit and a friend's are the same card — avatar, the day's tags, then its
 * machines in a small light font; the own one has a menu to move it, save it as a plan or delete
 * it.
 */
@Composable
private fun VisitCard(
    visit: CalendarVisitUi,
    me: PersonUi?,
    editable: Boolean,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onSaveAsPlan: () -> Unit,
    onRemove: () -> Unit,
) {
    DayCard(
        tag = "calendar-visit-${visit.id.value}",
        avatar = me?.let { { PersonAvatar(it.userId, it.name, it.avatar, size = 32.dp) } },
        tags = visit.tags,
        machines = visit.machines,
        onOpen = onOpen,
        menu =
            if (editable) {
                { VisitMenu(visit, onMove, onSaveAsPlan, onRemove) }
            } else {
                null
            },
    )
}

@Composable
private fun DayCard(
    tag: String,
    avatar: (@Composable () -> Unit)?,
    tags: String?,
    machines: String?,
    onOpen: () -> Unit,
    menu: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.onBackground.copy(alpha = 0.16f), shape)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        avatar?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tags?.let {
                Text(
                    it,
                    modifier = Modifier.testTag("$tag-tags"),
                    fontSize = 15.sp,
                    color = colors.onBackground,
                )
            }
            machines?.let {
                Text(
                    it,
                    modifier = Modifier.testTag("$tag-machines"),
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onBackground.copy(alpha = 0.55f),
                )
            }
        }
        menu?.invoke()
    }
}

@Composable
private fun VisitMenu(
    visit: CalendarVisitUi,
    onMove: () -> Unit,
    onSaveAsPlan: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(36.dp)
                .clip(ControlShape)
                .clickable { open = true }
                .testTag("visit-menu-${visit.id.value}"),
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
            DropdownMenuItem(
                text = { Text(strings().move) },
                leadingIcon = { Icon(PhosphorIcons.CalendarBlank, null, Modifier.size(20.dp)) },
                onClick = {
                    open = false
                    onMove()
                },
                modifier = Modifier.testTag("move-visit-${visit.id.value}"),
            )
            DropdownMenuItem(
                text = { Text(strings().saveAsPlan) },
                leadingIcon = { Icon(PhosphorIcons.ListChecks, null, Modifier.size(20.dp)) },
                onClick = {
                    open = false
                    onSaveAsPlan()
                },
                modifier = Modifier.testTag("save-as-plan-${visit.id.value}"),
            )
            DropdownMenuItem(
                text = { Text(strings().delete) },
                leadingIcon = { Icon(PhosphorIcons.Trash, null, Modifier.size(20.dp)) },
                onClick = {
                    open = false
                    onRemove()
                },
                modifier = Modifier.testTag("remove-visit-${visit.id.value}"),
            )
        }
    }
}
