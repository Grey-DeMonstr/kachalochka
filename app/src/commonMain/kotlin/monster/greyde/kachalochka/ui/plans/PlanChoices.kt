package monster.greyde.kachalochka.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.ui.strings.strings

data class PlanChoiceUi(
    val id: PlanId,
    val title: String,
)

/** Asks "Which plan?" with [choices], then "New plan". */
@Composable
internal fun PlanChoiceDialog(
    choices: List<PlanChoiceUi>,
    onChoose: (PlanId) -> Unit,
    onNewPlan: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings().choosePlan) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    PlanRow(choice.title, "plan-choice-${choice.id.value}") { onChoose(choice.id) }
                }
                PlanRow(strings().newPlan, "plan-choice-new", onNewPlan)
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("plan-cancel")) {
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

/** What the last placement or share said; a tap hides it. */
@Composable
internal fun PlacementNoticeText(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "placement-notice",
) {
    Text(
        text,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onDismiss)
                .padding(vertical = 8.dp)
                .testTag(tag),
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
    )
}

/**
 * Asks which plan takes the visit or the machines while [viewModel] offers its choices. A new plan
 * opens with the day of an own visit, or with the stand-ins of shown machines. [onStoodIn] follows
 * once another account's machine has the account's own standing in.
 */
@Composable
internal fun PlanChooser(
    viewModel: PlacementViewModel,
    onNewPlan: (CalendarDay) -> Unit = {},
    onNewPlanOfMachines: (List<MachineId>) -> Unit = {},
    onStoodIn: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val choices = state.choices ?: return
    PlanChoiceDialog(
        choices,
        onChoose = { viewModel.addTo(it, onStoodIn) },
        onNewPlan = { viewModel.newPlan(onNewPlan, onNewPlanOfMachines, onStoodIn) },
        onCancel = viewModel::cancelChoice,
    )
}

/** What the last placement of what was shown as [key] said; a tap hides it. */
@Composable
internal fun PlacementNotice(
    viewModel: PlacementViewModel,
    key: String,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val notice = state.notice?.takeIf { it.key == key } ?: return
    PlacementNoticeText(notice.text, viewModel::dismissNotice, modifier)
}
