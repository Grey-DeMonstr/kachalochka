package monster.greyde.kachalochka.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.ConfirmDialog
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.components.TextInput
import monster.greyde.kachalochka.ui.friends.OfflineNotice
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GuardiansScreen(onBack: () -> Unit) {
    val viewModel: GuardiansViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    Screen(strings().guardians, onBack = onBack, onOpenSettings = null) {
        if (state.offline) {
            OfflineNotice(onRetry = viewModel::load)
            return@Screen
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                strings().guardianRights,
                modifier = Modifier.testTag("guardian-rights"),
                fontSize = 15.sp,
                color = colors.onBackground.copy(alpha = 0.6f),
            )
            FamilyList(state.guardians, strings().noGuardians, "guardian", viewModel::askToRemove)
            SectionLabel(strings().addGuardian)
            TextInput(
                state.code,
                strings().guardianCode,
                viewModel::type,
                Modifier.fillMaxWidth().testTag("guardian-code-field"),
            )
            state.error?.let {
                Text(it, modifier = Modifier.testTag("guardian-code-error"), color = colors.error)
            }
            AccentButton(
                strings().add,
                PhosphorIcons.Plus,
                viewModel::add,
                Modifier.testTag("add-guardian"),
                enabled = state.canAdd,
            )
        }
        state.removing?.let {
            ConfirmDialog(
                strings().removeGuardianTitle,
                strings().removeGuardianText,
                strings().removeLink,
                "guardian-remove-confirm",
                "guardian-remove-cancel",
                onConfirm = viewModel::confirmRemove,
                onCancel = viewModel::cancelRemove,
            )
        }
    }
}
