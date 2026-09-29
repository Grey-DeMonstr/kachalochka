package monster.greyde.kachalochka.ui.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGroup: (GroupId) -> Unit,
) {
    val viewModel: GroupsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    // A return from a group reloads; the first mount already has data from the view model.
    LaunchedEffect(Unit) { viewModel.enter() }
    Screen(strings().friends, onBack = onBack, onOpenSettings = onOpenSettings) {
        if (state.offline) {
            OfflineNotice(onRetry = viewModel::load)
            return@Screen
        }
        val groups = state.groups
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                groups == null ->
                    Text(
                        strings().loading,
                        modifier = Modifier.padding(16.dp).testTag("groups-loading"),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                groups.isEmpty() ->
                    Text(
                        strings().noGroupsYet,
                        modifier = Modifier.padding(16.dp).testTag("groups-empty"),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                else ->
                    groups.forEach { row ->
                        GroupRow(row, onOpen = { onOpenGroup(row.id) })
                        Rule()
                    }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentButton(
                strings().createGroup,
                PhosphorIcons.Plus,
                viewModel::openCreate,
                Modifier.testTag("create-group"),
            )
            OutlineButton(
                strings().joinByCode,
                PhosphorIcons.ArrowRight,
                viewModel::openJoin,
                Modifier.fillMaxWidth().testTag("join-by-code"),
            )
        }
        state.dialog?.let { dialog ->
            GroupsDialog(
                dialog,
                onType = viewModel::type,
                onConfirm = { viewModel.confirm(onOpenGroup) },
                onCancel = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun GroupRow(
    row: GroupRowUi,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("group-row-${row.id.value}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(row.name, fontSize = 17.sp, color = colors.onBackground)
            Text(row.members, fontSize = 13.sp, color = colors.onBackground.copy(alpha = 0.52f))
        }
        Icon(
            PhosphorIcons.CaretRight,
            null,
            tint = colors.onBackground,
            modifier = Modifier.alpha(0.4f),
        )
    }
}

@Composable
private fun GroupsDialog(
    dialog: GroupsDialogUi,
    onType: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val creating = dialog.kind == GroupsDialogKind.Create
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (creating) strings().newGroup else strings().joinGroup) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.text,
                    onValueChange = onType,
                    singleLine = true,
                    modifier = Modifier.testTag("group-dialog-field"),
                )
                dialog.error?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("group-dialog-error"),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = dialog.canConfirm,
                modifier = Modifier.testTag("group-dialog-confirm"),
            ) {
                Text(if (creating) strings().create else strings().join)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("group-dialog-cancel")) {
                Text(strings().cancel)
            }
        },
    )
}
