package monster.greyde.kachalochka.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.components.ControlShape
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.TextInput
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel

/** The active account: how it signs in, signing out, and deleting it. */
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val viewModel: AccountViewModel = koinViewModel()
    val email by viewModel.email.collectAsState()
    val deletion by viewModel.deletion.collectAsState()
    // Signed out, deleted or switched to a child: there is no account to show.
    LaunchedEffect(email == null) { if (email == null) onBack() }
    val colors = MaterialTheme.colorScheme
    val muted = colors.onBackground.copy(alpha = 0.55f)
    Screen(strings().account, onBack = onBack, onOpenSettings = null) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        PhosphorIcons.GoogleLogo,
                        null,
                        tint = colors.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            email.orEmpty(),
                            Modifier.testTag("account-email"),
                            fontSize = 16.sp,
                            color = colors.onBackground,
                        )
                        Text(strings().signedInWithGoogle, fontSize = 13.5.sp, color = muted)
                    }
                }
                RowDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clickable(onClick = viewModel::signOut)
                        .padding(horizontal = 14.dp)
                        .testTag("settings-sign-out"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        PhosphorIcons.SignOut,
                        null,
                        tint = colors.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        strings().signOut,
                        Modifier.testTag("settings-sign-out-label"),
                        fontSize = 16.sp,
                        color = colors.onBackground,
                    )
                }
            }
            SettingsGroup(strings().dangerZone) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(ControlShape)
                        .border(1.dp, colors.error, ControlShape)
                        .clickable(enabled = !deletion.running, onClick = viewModel::askToDelete)
                        .testTag("delete-account"),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp,
                            Alignment.CenterHorizontally,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        PhosphorIcons.Trash,
                        null,
                        tint = colors.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(strings().deleteAccount, fontSize = 16.sp, color = colors.error)
                }
                Text(
                    strings().deleteAccountHint,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    color = muted,
                )
                deletion.error?.let {
                    Text(
                        it,
                        Modifier.testTag("delete-account-error"),
                        fontSize = 14.sp,
                        color = colors.error,
                    )
                }
            }
        }
    }
    if (deletion.confirming) DeleteDialog(deletion, viewModel)
}

@Composable
private fun DeleteDialog(
    deletion: DeletionUi,
    viewModel: AccountViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelDelete,
        title = { Text(strings().deleteAccountTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings().deleteAccountText(DELETE_WORD))
                TextInput(
                    deletion.word,
                    DELETE_WORD,
                    viewModel::typeDeleteWord,
                    Modifier.testTag("delete-word"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::confirmDelete,
                enabled = deletion.canConfirm,
                modifier = Modifier.testTag("confirm-delete-account"),
            ) { Text(strings().delete, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::cancelDelete,
                modifier = Modifier.testTag("cancel-delete-account"),
            ) { Text(strings().cancel) }
        },
    )
}
