package monster.greyde.kachalochka.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings

/** Friends' data is read online only; every friends screen shows this instead of crashing. */
@Composable
internal fun OfflineNotice(onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            strings().offline,
            modifier = Modifier.testTag("friends-offline"),
            fontSize = 15.sp,
            color = colors.onBackground.copy(alpha = 0.6f),
        )
        OutlineButton(
            strings().retry,
            PhosphorIcons.ArrowRight,
            onRetry,
            Modifier.fillMaxWidth().testTag("friends-retry"),
        )
    }
}

@Composable
internal fun InviteConfirmDialog(
    onJoin: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings().joinTitle) },
        text = { Text(strings().joinText) },
        confirmButton = {
            TextButton(onClick = onJoin, modifier = Modifier.testTag("invite-confirm")) {
                Text(strings().join)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("invite-cancel")) {
                Text(strings().cancel)
            }
        },
    )
}

@Composable
internal fun InviteMissingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("invite-missing"),
        title = { Text(strings().inviteNotFound) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("invite-missing-ok")) {
                Text(strings().understood)
            }
        },
    )
}
