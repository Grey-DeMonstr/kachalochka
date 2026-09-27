package monster.greyde.kachalochka.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FriendVisitScreen(
    member: UserId,
    name: String,
    day: CalendarDay,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: FriendVisitViewModel = koinViewModel { parametersOf(member, name, day) }
    val state by viewModel.state.collectAsState()
    val offline by viewModel.offline.collectAsState()
    // The view model already loads once created: it follows accounts.activeId from init.
    Screen(state?.title ?: name, onBack = onBack, onOpenSettings = onOpenSettings) {
        val current = state
        if (offline) {
            OfflineNotice(onRetry = viewModel::refresh)
        } else if (current != null) {
            FriendVisitList(current)
        }
    }
}

@Composable
private fun FriendVisitList(state: FriendVisitUiState) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            state.setCountLabel.uppercase(),
            modifier = Modifier.testTag("visit-set-count"),
            fontSize = 13.sp,
            letterSpacing = 0.09.em,
            color = colors.onBackground.copy(alpha = 0.5f),
        )
        if (state.groups.isEmpty()) {
            Text(
                "Нет визита",
                modifier = Modifier.padding(top = 12.dp).testTag("friend-visit-empty"),
                fontSize = 16.sp,
                color = colors.onBackground.copy(alpha = 0.6f),
            )
        }
        state.groups.forEach { group ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .testTag("friend-group-${group.machineId.value}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.title,
                    fontSize = 16.sp,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    group.summary,
                    fontSize = 15.sp,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            }
            group.sets.forEach { row -> FriendSetRow(row) }
            Rule()
        }
    }
}

@Composable
private fun FriendSetRow(row: FriendSetRowUi) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("friend-set-${row.id.value}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.title,
            fontSize = 16.sp,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.value,
            fontSize = 15.sp,
            color = colors.onBackground.copy(alpha = 0.6f),
        )
    }
}
