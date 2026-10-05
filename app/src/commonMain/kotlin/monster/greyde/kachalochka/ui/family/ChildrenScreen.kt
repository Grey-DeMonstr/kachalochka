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
import monster.greyde.kachalochka.ui.components.OutlineButton
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.friends.OfflineNotice
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChildrenScreen(onBack: () -> Unit) {
    val viewModel: ChildrenViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    Screen(strings().children, onBack = onBack, onOpenSettings = null) {
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
            FamilyList(state.children, strings().noChildren, "child", viewModel::askToRemove)
            state.code?.let { code ->
                Text(
                    strings().childCode(code),
                    modifier = Modifier.testTag("child-code"),
                    fontSize = 17.sp,
                    color = colors.onBackground,
                )
                state.link?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("child-code-link"),
                        fontSize = 14.sp,
                        color = colors.onBackground,
                    )
                }
                Text(
                    strings().childCodeHint,
                    modifier = Modifier.testTag("child-code-hint"),
                    fontSize = 14.sp,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
                OutlineButton(
                    strings().share,
                    PhosphorIcons.ShareNetwork,
                    viewModel::share,
                    Modifier.fillMaxWidth().testTag("child-code-share"),
                )
            }
            state.notice?.let {
                Text(
                    it,
                    modifier = Modifier.testTag("children-notice"),
                    fontSize = 15.sp,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            }
            AccentButton(
                strings().addChild,
                PhosphorIcons.Plus,
                viewModel::addChild,
                Modifier.testTag("add-child"),
            )
        }
        state.removing?.let {
            ConfirmDialog(
                strings().removeChildTitle,
                strings().removeChildText,
                strings().removeLink,
                "child-remove-confirm",
                "child-remove-cancel",
                onConfirm = viewModel::confirmRemove,
                onCancel = viewModel::cancelRemove,
            )
        }
    }
}
