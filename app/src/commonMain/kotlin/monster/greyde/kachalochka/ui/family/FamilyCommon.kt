package monster.greyde.kachalochka.ui.family

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import monster.greyde.kachalochka.ui.strings.strings

@Composable
internal fun GuardianInviteDialog(
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings().guardianInviteTitle) },
        text = { Text(strings().guardianRights) },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                modifier = Modifier.testTag("guardian-invite-confirm"),
            ) { Text(strings().add) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("guardian-invite-cancel")) {
                Text(strings().cancel)
            }
        },
    )
}

@Composable
internal fun GuardianProblemDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("guardian-problem"),
        title = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("guardian-problem-ok")) {
                Text(strings().understood)
            }
        },
    )
}
