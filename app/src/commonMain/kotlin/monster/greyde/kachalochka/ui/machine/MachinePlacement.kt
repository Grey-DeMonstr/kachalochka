package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.strings

/** The ⋮ menu of a machine screen, shown with each item whose action is given. */
@Composable
internal fun MachineMenu(
    onToPlan: (() -> Unit)? = null,
    onToday: (() -> Unit)? = null,
    onUnlink: (() -> Unit)? = null,
) {
    if (onToPlan == null && onToday == null && onUnlink == null) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        SquareIconButton(
            PhosphorIcons.DotsThreeVertical,
            strings().more,
            { expanded = true },
            Modifier.testTag("machine-menu"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onToPlan?.let {
                MenuItem(
                    strings().toPlan,
                    "machine-to-plan",
                    it,
                ) { expanded = false }
            }
            onToday?.let {
                MenuItem(
                    strings().toToday,
                    "machine-to-today",
                    it,
                ) { expanded = false }
            }
            onUnlink?.let {
                MenuItem(strings().unlinkFromFriends, "unlink-machine", it) { expanded = false }
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    tag: String,
    onClick: () -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = {
            close()
            onClick()
        },
        modifier = Modifier.testTag(tag),
    )
}

/**
 * Asks which plan takes the machine while [viewModel] offers its choices. [onStoodIn] follows
 * once a friend's machine has the account's machine standing in.
 */
@Composable
internal fun PlanChooser(
    viewModel: MachinePlacementViewModel,
    onNewPlan: (MachineId) -> Unit,
    onStoodIn: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val choices = state.choices ?: return
    AlertDialog(
        onDismissRequest = viewModel::cancelChoice,
        title = { Text(strings().choosePlan) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    PlanRow(choice.title, "plan-choice-${choice.id.value}") {
                        viewModel.addTo(choice.id, onStoodIn)
                    }
                }
                PlanRow(strings().newPlan, "plan-choice-new") {
                    viewModel.newPlan(onStoodIn, onNewPlan)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::cancelChoice,
                modifier = Modifier.testTag("plan-cancel"),
            ) {
                Text(strings().cancel)
            }
        },
    )
}

@Composable
private fun PlanRow(
    text: String,
    tag: String,
    onClick: () -> Unit,
) {
    Text(
        text,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp)
                .testTag(tag),
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** What the last placement of [machine] said; a tap hides it. */
@Composable
internal fun PlacementNotice(
    viewModel: MachinePlacementViewModel,
    machine: MachineId,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val notice = state.notice?.takeIf { it.machine == machine } ?: return
    Text(
        notice.text,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::dismissNotice)
                .padding(vertical = 8.dp)
                .testTag("placement-notice"),
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
    )
}
