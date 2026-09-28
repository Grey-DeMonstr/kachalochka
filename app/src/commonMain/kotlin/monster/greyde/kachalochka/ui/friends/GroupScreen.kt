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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GroupScreen(
    groupId: GroupId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMember: (Friend) -> Unit,
    onGone: () -> Unit,
) {
    val viewModel: GroupViewModel = koinViewModel { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()
    val offline by viewModel.offline.collectAsState()
    val gone by viewModel.gone.collectAsState()
    LaunchedEffect(gone) { if (gone) onGone() }
    // The view model already loads once created: it follows accounts.activeId from init.
    Screen(state?.title ?: "Группа", onBack = onBack, onOpenSettings = onOpenSettings) {
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
            SectionLabel("Участники")
            current.members.forEach { row ->
                MemberRow(row, onOpen = { onOpenMember(row.friend) })
                Rule()
            }
            Text(
                "Код приглашения: ${current.code}",
                modifier = Modifier.testTag("invite-code"),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            current.notice?.let {
                Text(
                    it,
                    modifier = Modifier.testTag("group-notice"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            if (current.isOwner) {
                OutlineButton(
                    "Удалить группу",
                    PhosphorIcons.Trash,
                    viewModel::askToGo,
                    Modifier.fillMaxWidth().testTag("delete-group"),
                )
            } else {
                OutlineButton(
                    "Выйти из группы",
                    PhosphorIcons.ArrowLeft,
                    viewModel::askToGo,
                    Modifier.fillMaxWidth().testTag("leave-group"),
                )
            }
        }
        current.confirming?.let { confirm ->
            ConfirmDialog(
                confirm.title,
                confirm.text,
                confirm.confirmLabel,
                "group-confirm",
                "group-cancel",
                onConfirm = viewModel::confirm,
                onCancel = viewModel::cancel,
            )
        }
    }
}

@Composable
private fun MemberRow(
    row: MemberRowUi,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = row.opens, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("member-${row.friend.userId.value}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.friend.displayName, fontSize = 17.sp, color = colors.onBackground)
        if (row.owner) {
            Text(
                "владелец",
                modifier = Modifier.testTag("member-owner-${row.friend.userId.value}"),
                fontSize = 13.sp,
                color = colors.secondary,
            )
        }
    }
}
