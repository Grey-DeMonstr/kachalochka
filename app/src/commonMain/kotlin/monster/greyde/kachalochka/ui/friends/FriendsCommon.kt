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

/** Friends' data is read online only; every friends screen shows this instead of crashing. */
@Composable
internal fun OfflineNotice(onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Нет связи с сервером",
            modifier = Modifier.testTag("friends-offline"),
            fontSize = 15.sp,
            color = colors.onBackground.copy(alpha = 0.6f),
        )
        OutlineButton(
            "Повторить",
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
        title = { Text("Вступить в группу по приглашению?") },
        text = { Text("Участники группы увидят ваши визиты и тренажёры.") },
        confirmButton = {
            TextButton(onClick = onJoin, modifier = Modifier.testTag("invite-confirm")) {
                Text("Вступить")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("invite-cancel")) {
                Text("Отмена")
            }
        },
    )
}

@Composable
internal fun InviteMissingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("invite-missing"),
        title = { Text("Приглашение не найдено") },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("invite-missing-ok")) {
                Text("Понятно")
            }
        },
    )
}
