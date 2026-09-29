package monster.greyde.kachalochka.ui.friends

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.calendar.MonthGrid
import monster.greyde.kachalochka.ui.calendar.MonthHeader
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FriendCalendarScreen(
    member: UserId,
    name: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVisit: (CalendarDay) -> Unit,
) {
    val viewModel: FriendCalendarViewModel = koinViewModel { parametersOf(member) }
    val state by viewModel.state.collectAsState()
    val offline by viewModel.offline.collectAsState()
    // The view model already loads once created: it follows accounts.activeId from init.
    Screen(name, onBack = onBack, onOpenSettings = onOpenSettings) {
        if (offline) {
            OfflineNotice(onRetry = viewModel::refresh)
            return@Screen
        }
        val current = state ?: return@Screen
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
            MonthGrid(current.weeks, onSelect = viewModel::selectDay)
            Rule()
            Text(
                current.dayTitle,
                modifier = Modifier.testTag("calendar-day-title"),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            current.visit?.let { visit ->
                FriendDayCard(visit, onOpen = { onOpenVisit(current.day) })
            }
            if (current.noVisit) {
                Text(
                    strings().noVisit,
                    modifier = Modifier.testTag("calendar-empty"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun FriendDayCard(
    visit: FriendDayUi,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.onBackground.copy(alpha = 0.16f), shape)
            .clickable(onClick = onOpen)
            .padding(12.dp)
            .testTag("friend-day-visit"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(visit.counts, fontSize = 15.sp, color = colors.onBackground)
        visit.machines?.let {
            Text(
                it,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}
