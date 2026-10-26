package monster.greyde.kachalochka.ui.family

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.strings.strings

/**
 * Asks once, while [ready], whether to link by the code [pending] kept from a link: the account
 * agrees before anyone sees its gym. [active] asks again for another account.
 */
@Composable
internal fun PendingLinkPrompt(
    pending: PendingLink,
    active: UserId?,
    ready: Boolean,
    title: String,
    text: String,
    tag: String,
    onLinked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var offered by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active, ready) { offered = ready && pending.waiting }
    if (offered) {
        LinkInviteDialog(
            title,
            text,
            tag,
            onAccept = {
                offered = false
                scope.launch {
                    when (pending.consume()) {
                        is Acceptance.Linked -> onLinked()
                        Acceptance.UnknownCode -> problem = AppStrings.current.guardianCodeUnknown
                        Acceptance.OwnCode -> problem = AppStrings.current.ownGuardianCode
                        null -> Unit
                    }
                }
            },
            onCancel = {
                offered = false
                pending.decline()
            },
        )
    }
    problem?.let { GuardianProblemDialog(it, onDismiss = { problem = null }) }
}

/** [tag] starts the test tags of the two buttons. */
@Composable
private fun LinkInviteDialog(
    title: String,
    text: String,
    tag: String,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onAccept, modifier = Modifier.testTag("$tag-confirm")) {
                Text(strings().add)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("$tag-cancel")) {
                Text(strings().cancel)
            }
        },
    )
}

@Composable
private fun GuardianProblemDialog(
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
