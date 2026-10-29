package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.ui.components.SquareIconButton
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.plans.PlacementNoticeText
import monster.greyde.kachalochka.ui.plans.PlanChoiceDialog
import monster.greyde.kachalochka.ui.strings.strings

/** The ⋮ menu of a machine screen, shown with each item whose action is given. */
@Composable
internal fun MachineMenu(
    onToPlan: (() -> Unit)? = null,
    onToday: (() -> Unit)? = null,
    onUnlink: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    if (listOfNotNull(onToPlan, onToday, onUnlink, onDelete).isEmpty()) return
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
                    strings().repeatToday,
                    "machine-to-today",
                    it,
                ) { expanded = false }
            }
            onUnlink?.let {
                MenuItem(strings().unlinkFromFriends, "unlink-machine", it) { expanded = false }
            }
            onDelete?.let {
                MenuItem(strings().deleteMachine, "delete-machine", it) { expanded = false }
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
    PlanChoiceDialog(
        choices,
        onChoose = { viewModel.addTo(it, onStoodIn) },
        onNewPlan = { viewModel.newPlan(onStoodIn, onNewPlan) },
        onCancel = viewModel::cancelChoice,
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
    PlacementNoticeText(notice.text, viewModel::dismissNotice, modifier)
}
