package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.friends.OfflineNotice
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.photos.PhotoStrip
import monster.greyde.kachalochka.ui.photos.PhotoViewer
import monster.greyde.kachalochka.ui.photos.ShownPhoto
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FriendMachineScreen(
    machineId: MachineId,
    ownerId: UserId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onTaken: (MachineId) -> Unit,
) {
    val viewModel: FriendMachineViewModel = koinViewModel { parametersOf(machineId, ownerId) }
    LaunchedEffect(Unit) { viewModel.speak() }
    val state by viewModel.state.collectAsState()
    val offline by viewModel.offline.collectAsState()
    var opened by remember { mutableStateOf<ShownPhoto?>(null) }
    // The view model already loads once created: it follows accounts.activeId from init.
    Screen(strings().friendMachine, onBack = onBack, onOpenSettings = onOpenSettings) {
        val current = state
        if (offline) {
            OfflineNotice(onRetry = viewModel::refresh)
        } else if (current != null) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    Text(
                        current.name,
                        modifier = Modifier.testTag("friend-machine-name"),
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        current.owner,
                        modifier = Modifier.testTag("friend-machine-owner"),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                PhotoStrip(current.photos, launchers = null, onOpen = { opened = it })
                if (current.note.isNotBlank()) {
                    Setting(strings().setupNote, current.note, "friend-machine-note")
                }
                Setting(strings().howWeightCounts, current.caption, "friend-machine-caption")
                current.platform?.let {
                    Setting(
                        strings().platformWeight,
                        it,
                        "friend-machine-platform",
                    )
                }
            }
            if (current.canTake) {
                Box(Modifier.padding(16.dp)) {
                    AccentButton(
                        strings().takeForMyself,
                        PhosphorIcons.Copy,
                        { viewModel.take(onTaken) },
                        Modifier.testTag("take-machine"),
                    )
                }
            }
        }
    }
    opened?.let { PhotoViewer(it, onClose = { opened = null }) }
}

@Composable
private fun Setting(
    label: String,
    value: String,
    tag: String,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(label, fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.48f))
        Text(
            value,
            modifier = Modifier.testTag(tag),
            fontSize = 16.sp,
            color = colors.onBackground,
        )
    }
}
