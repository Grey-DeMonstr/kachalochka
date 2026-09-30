package monster.greyde.kachalochka.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.friends.FRIEND_PALETTE_SIZE
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.ui.account.PersonAvatar
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.friendColor
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
    LaunchedEffect(Unit) { viewModel.speak() }
    val state by viewModel.state.collectAsState()
    val offline by viewModel.offline.collectAsState()
    val gone by viewModel.gone.collectAsState()
    LaunchedEffect(gone) { if (gone) onGone() }
    // The view model already loads once created: it follows accounts.activeId from init.
    Screen(state?.title ?: strings().group, onBack = onBack, onOpenSettings = onOpenSettings) {
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
            SectionLabel(strings().membersSection)
            current.members.forEach { row ->
                MemberRow(
                    row,
                    onOpen = { onOpenMember(row.friend) },
                    onPickColor = { viewModel.pickColor(row.friend.userId) },
                )
                Rule()
            }
            Text(
                strings().inviteCode(current.code),
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
            AccentButton(
                strings().invite,
                PhosphorIcons.UsersThree,
                viewModel::invite,
                Modifier.testTag("invite"),
            )
            if (current.isOwner) {
                OutlineButton(
                    strings().deleteGroup,
                    PhosphorIcons.Trash,
                    viewModel::askToGo,
                    Modifier.fillMaxWidth().testTag("delete-group"),
                )
            } else {
                OutlineButton(
                    strings().leaveGroup,
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
        if (current.colorPicker != null) {
            ColorPicker(onChoose = viewModel::chooseColor, onDismiss = viewModel::dismissColor)
        }
    }
}

@Composable
private fun MemberRow(
    row: MemberRowUi,
    onOpen: () -> Unit,
    onPickColor: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = row.opens, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("member-${row.friend.userId.value}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            row.friend.userId,
            row.friend.displayName,
            row.friend.avatar,
            size = 44.dp,
            tint = row.color?.let { friendColor(it) },
        )
        Text(
            row.friend.displayName,
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            color = colors.onBackground,
        )
        if (row.owner) {
            Text(
                strings().owner,
                modifier = Modifier.testTag("member-owner-${row.friend.userId.value}"),
                fontSize = 13.sp,
                color = colors.secondary,
            )
        }
        val index = row.color
        if (index != null) {
            // The dot is small; the circle around it is what takes the tap.
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPickColor)
                    .semantics { contentDescription = colorName(index) }
                    .testTag("member-color-${row.friend.userId.value}"),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(friendColor(index)))
            }
        }
    }
}

private fun colorName(index: Int) = AppStrings.current.colorName(index + 1)

@Composable
private fun ColorPicker(
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("color-picker"),
        title = { Text(strings().calendarColor) },
        // Wraps only where a narrow phone cannot fit the eight in one row.
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(FRIEND_PALETTE_SIZE) { index ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(friendColor(index))
                            .clickable { onChoose(index) }
                            .semantics { contentDescription = colorName(index) }
                            .testTag("color-option-$index"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("color-cancel")) {
                Text(strings().cancel)
            }
        },
    )
}
