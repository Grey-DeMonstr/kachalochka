package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MachineListScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMachine: (MachineId) -> Unit,
    onNewMachine: () -> Unit,
    onOpenFriendMachine: (MachineId, owner: UserId) -> Unit,
) {
    val viewModel: MachineListViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    Screen(strings().machines, onBack = onBack, onOpenSettings = onOpenSettings) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val cards = state.own
            when {
                cards == null -> Unit
                cards.isEmpty() ->
                    Text(
                        strings().noMachinesYet,
                        modifier = Modifier.padding(16.dp).testTag("machine-list-empty"),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                else ->
                    cards.forEach { card ->
                        MachineCard(card, "machine-list-row-${card.id.value}") {
                            onOpenMachine(card.id)
                        }
                    }
            }
            state.friendSections.forEach { section ->
                FriendSectionHeader(
                    section,
                    Modifier.testTag("machine-list-friend-section-${section.friend.userId.value}"),
                )
                section.cards.forEach { card ->
                    MachineCard(card, "machine-list-friend-${card.id.value}") {
                        onOpenFriendMachine(card.id, section.friend.userId)
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp)) {
            OutlineButton(
                strings().add,
                PhosphorIcons.Plus,
                onNewMachine,
                Modifier.fillMaxWidth().testTag("new-machine"),
            )
        }
    }
}
