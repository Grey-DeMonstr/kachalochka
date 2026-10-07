package monster.greyde.kachalochka.ui.friends

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.machine.LinkedMachines
import monster.greyde.kachalochka.ui.photos.MachineThumbnail
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.visit.MachineLines
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FriendVisitScreen(
    member: UserId,
    name: String,
    day: CalendarDay,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMachine: (MachineId) -> Unit,
) {
    val viewModel: FriendVisitViewModel = koinViewModel { parametersOf(member, name, day) }
    LaunchedEffect(Unit) { viewModel.speak() }
    val state by viewModel.state.collectAsState()
    val offline by viewModel.offline.collectAsState()
    // The view model already loads once created: it follows accounts.activeId from init.
    Screen(
        state?.title ?: name,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        leading = {
            state?.let {
                PersonAvatar(
                    member,
                    it.title,
                    it.avatar,
                    Modifier.testTag("friend-visit-avatar"),
                    size = 32.dp,
                )
            }
        },
    ) {
        val current = state
        if (offline) {
            OfflineNotice(onRetry = viewModel::refresh)
        } else if (current != null) {
            FriendVisitList(current, onOpenMachine)
        }
    }
}

@Composable
private fun FriendVisitList(
    state: FriendVisitUiState,
    onOpenMachine: (MachineId) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            state.day,
            modifier = Modifier.testTag("friend-visit-day"),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onBackground,
        )
        Text(
            state.countLabel.uppercase(),
            modifier = Modifier.padding(top = 4.dp).testTag("friend-visit-count"),
            fontSize = 13.sp,
            letterSpacing = 0.09.em,
            color = colors.onBackground.copy(alpha = 0.5f),
        )
        if (state.groups.isEmpty()) {
            Text(
                strings().noVisit,
                modifier = Modifier.padding(top = 12.dp).testTag("friend-visit-empty"),
                fontSize = 16.sp,
                color = colors.onBackground.copy(alpha = 0.6f),
            )
        }
        state.groups.forEach { group ->
            val id = group.machineId.value
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMachine(group.machineId) }
                    .padding(vertical = 12.dp)
                    .testTag("friend-group-$id"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MachineThumbnail(group.photo, PhosphorIcons.Barbell, size = 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MachineLines(
                        title = group.title,
                        tags = group.tags,
                        note = group.note,
                        summary = group.summary,
                        tag = id,
                    )
                    if (group.linkedWith.isNotEmpty()) {
                        LinkedMachines(group.linkedWith, "friend-linked-$id")
                    }
                }
            }
            Rule()
        }
    }
}
